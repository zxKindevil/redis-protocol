package redis.loader;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.Socket;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import redis.RedisProtocol;

public class Loader {

  @Argument(alias = "r", description = "Regular expression to parse lines", required = true)
  private static String regex;

  @Argument(alias = "k", description = "Key column", required = true)
  private static String key;

  @Argument(alias = "p", description = "Key prefix")
  private static String prefix;

  @Argument(alias = "c", description = "Column groups", delimiter = ",")
  private static String[] columns;

  @Argument(alias = "n", description = "Column names", delimiter = ",")
  private static String[] names;

  @Argument(alias = "h", description = "Redis host")
  private static String host = "localhost";

  @Argument(alias = "p", description = "Redis port")
  private static Integer port = 6379;
  private static final byte[] HMSET = "HMSET".getBytes();

  public static void main(String[] args) throws IOException {
    final List<String> parse;
    try {
      parse = Args.parse(Loader.class, args);
    } catch (IllegalArgumentException e) {
      Args.usage(Loader.class);
      System.exit(1);
      return;
    }

    BufferedReader br;
    if (parse.isEmpty()) {
      br = new BufferedReader(new InputStreamReader(System.in));
    } else {
      br = new BufferedReader(new Reader() {
        Reader reader;
        Iterator<String> files = parse.iterator();

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
          if (reader == null) {
            if (files.hasNext()) {
              reader = new FileReader(files.next());
            } else {
              return -1;
            }
          }
          int read = reader.read(cbuf, off, len);
          if (read == -1) {
            reader = null;
            return read(cbuf, off, len);
          }
          return read;
        }

        @Override
        public void close() throws IOException {
        }
      });
    }

    Pattern pattern = Pattern.compile(regex);

    RedisProtocol redisProtocol = new RedisProtocol(new Socket(host, port));


    String line;
    while ((line = br.readLine()) != null) {
      Matcher matcher = pattern.matcher(line);
      if (matcher.matches()) {
        String keyValue = matcher.group(Integer.parseInt(key));
        Object[] objects = new Object[1 /* hmset */ + 1 /* key */ + names.length * 2 /* key/values */];
        objects[0] = HMSET;
        objects[1] = prefix == null ? keyValue : prefix + keyValue;
        for (int i = 0; i < names.length; i++) {
          objects[2 + i*2] = names[i];
          objects[2 + i*2 + 1] = matcher.group(Integer.parseInt(columns[i]));
        }
        redisProtocol.sendAsync(objects);
      }
    }

  }
}
