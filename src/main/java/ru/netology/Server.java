package ru.netology;

import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static org.apache.http.client.utils.URLEncodedUtils.parse;

public class Server implements Handler {
    public static final String GET = "GET";
    public static final String POST = "POST";

    final List<String> allowedMethods = List.of(GET, POST);
    private final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");
    private final ExecutorService service = Executors.newFixedThreadPool(64);
    private final Map<String, Map<String, Handler>> handlers = new ConcurrentHashMap<>();

    /**
     * Запускает сервер
     */
    public void start(int port) {
        try (final var serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                service.execute(() -> {
                    try (var in = new BufferedInputStream(socket.getInputStream());
                         var out = new BufferedOutputStream(socket.getOutputStream())) {
                        handler(out, in);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            service.shutdown();
        }
    }

    /**
     * Обрабатывает запрос,
     * парсит HTTP-сообщение,
     * формирует и отправляет ответ в зависимости от полученного запроса
     *
     * @param out - выходной поток
     * @param in  - входной поток
     */
    private void handler(BufferedOutputStream out, BufferedInputStream in) throws Exception {
        try {
            //задание лимита на request line + заголовки
            final var limit = 4096;
            in.mark(4096);

            final var buffer = new byte[limit];
            final var read= in.read(buffer);

            //ищем request line
            final var requestLineDelimiter = new byte[]{'\r', '\n'};
            final  var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
            if(requestLineEnd == -1) {
                badRequest(out);
                return;
            }

            //читаем request line
            final var requestLine = new String(Arrays.copyOf(buffer,requestLineEnd)).split(" ");
            if(requestLine.length != 3) {
                badRequest(out);
                return;
            }

            //Парсинг requestLine
            var methodName = requestLine[0];
            if (!allowedMethods.contains(methodName)) {
                badRequest(out);
                return;
            }

            final var path1 = requestLine[1];
            if(!path1.startsWith("/")) {
                badRequest(out);
                return;
            }
            final  var path = path1.substring(0, path1.indexOf('?'));
            final  var queryParametersEnc = path1.substring(path1.indexOf('?') + 1);


            //парсинг заголовков
            List<NameValuePair> list = URLEncodedUtils.parse(queryParametersEnc, StandardCharsets.UTF_8);

            //Чтение и парсинг headers
            final var headersStart =  requestLineEnd + requestLineDelimiter.length;
            in.reset();
            in.skip(headersStart);
            final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
            final  var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
            final var headersBytes = in.readNBytes(headersEnd - headersStart);
            final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));



            var request = new Request(methodName, path, headers);
            if (list != null && !list.isEmpty()) {
                request.setQueryParams(list);
            }
            byte [] bodyBytes=null;
            //Получение тела HTTP сообщения
            if(!methodName.equals(GET)){
                in.reset();
                in.skip(headersEnd+headersDelimiter.length);
                final var contentLength = extractHeader(headers, "Content-Length");
                if(contentLength.isPresent()){
                    final var length = Integer.parseInt(contentLength.get());
                    bodyBytes = in.readNBytes(length);
                }
            }

            if (bodyBytes != null) {
                request.setBody(bodyBytes);
            }

            System.out.println(request);

            //Формирование ответа в зависимости от полученного запроса
            Map<String, Handler> map = handlers.get(request.getNameMethod());
            if (map != null) {
                Handler handler = map.get(request.getPath());
                if (handler != null) {
                    handler.handle(request, out);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /**
     * Формирует и отправляет ответ при неверном запросе
     *
     * @param out - входной поток
     */
    private static void badRequest(BufferedOutputStream out) {
        try {
            out.write(("HTTP/1.1 404 Not Found\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n" + "\r\n")
                    .getBytes());
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Добавляет обработчик в мапу
     *
     * @param method  - имя метода
     * @param path    - путь
     * @param handler - обработчик
     */
    public void addHandler(String method, String path, Handler handler) {
        Map<String, Handler> map = new HashMap<>();
        map.put(path, handler);
        handlers.put(method, map);
    }

    @Override
    public void handle(Request request, BufferedOutputStream responseStream) {
    }

    /**
     * Формирует и отправляет ответ при верном запросе
     */
    public void response(BufferedOutputStream responseStream, Path filePath, String mimeType, long length) {
        try {
            var status = "HTTP/1.1 200 OK";
            responseStream.write((status + "\r\n" + "Content-Type: " + mimeType + "\r\n" + "Content-Length: " + length + "\r\n" + "Connection: close\r\n" + "\r\n").getBytes());
            Files.copy(filePath, responseStream);
            responseStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}
