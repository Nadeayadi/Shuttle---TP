package com.simplecity.amp_library.http;

import android.util.Log;
import fi.iki.elonen.NanoHTTPD;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HttpServer {

    private static final String TAG = "HttpServer";
    private static final String MIME_TEXT_HTML = "text/html";
    private static final String MIME_TEXT_PLAIN = "text/plain";
    private static final String MIME_APPLICATION_OCTET_STREAM = "application/octet-stream";
    private static final String MIME_IMAGE_PNG = "image/png";

    private NanoServer server;

    private String audioFileToServe;
    private byte[] imageBytesToServe;

    private FileInputStream audioInputStream;
    private ByteArrayInputStream imageInputStream;

    private boolean isStarted = false;

    public static HttpServer getInstance() {
        return new HttpServer();
    }

    private HttpServer() {
        server = new NanoServer();
    }

    public void serveAudio(String audioUri) {
        if (audioUri != null) {
            audioFileToServe = audioUri;
        }
    }

    public void serveImage(byte[] imageBytes) {
        if (imageBytes != null) {
            imageBytesToServe = imageBytes;
        }
    }

    public void clearImage() {
        imageBytesToServe = null;
    }

    public void start() {
        if (!isStarted) {
            try {
                server.start();
                isStarted = true;
            } catch (IOException e) {
                Log.e(TAG, "Error starting server: " + e.getMessage());
            }
        }
    }

    public void stop() {
        if (isStarted) {
            server.stop();
            isStarted = false;
            cleanupAudioStream();
            cleanupImageStream();
        }
    }

    private class NanoServer extends NanoHTTPD {

        NanoServer() {
            super(5000);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            if (uri.contains("audio")) {
                return serveAudio(session);
            } else if (uri.contains("image")) {
                return serveImage();
            }

            Log.e(TAG, "Returning NOT_FOUND response");
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_TEXT_HTML, "File not found");
        }

        private Response serveAudio(IHTTPSession session) {
            if (audioFileToServe == null) {
                Log.e(TAG, "Audio file to serve null");
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_TEXT_HTML, "File not found");
            }

            try {
                File file = new File(audioFileToServe);
                String range = session.getHeaders().get("range");
                if (range == null) {
                    range = "bytes=0-";
                }

                long fileLength = file.length();
                long start;
                long end;
                String rangeValue = range.trim().substring("bytes=".length());

                if (rangeValue.startsWith("-")) {
                    end = fileLength - 1;
                    start = fileLength - 1 - Long.parseLong(rangeValue.substring(1));
                } else {
                    String[] ranges = rangeValue.split("-");
                    start = Long.parseLong(ranges[0]);
                    end = ranges.length > 1 && !ranges[1].isEmpty() ? Long.parseLong(ranges[1]) : fileLength - 1;
                }

                if (end > fileLength - 1) {
                    end = fileLength - 1;
                }

                if (start <= end) {
                    long contentLength = end - start + 1;
                    cleanupAudioStream();
                    audioInputStream = new FileInputStream(file);
                    skipFully(audioInputStream, start);
                    String mimeType = getMimeType(audioFileToServe);
                    Response response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, audioInputStream, contentLength);
                    response.addHeader("Content-Length", String.valueOf(contentLength));
                    response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                    response.addHeader("Content-Type", mimeType);
                    return response;
                } else {
                    return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_TEXT_HTML, range);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error serving audio: " + e.getMessage());
                e.printStackTrace();
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_TEXT_HTML, "Error serving audio");
            }
        }

        private Response serveImage() {
            if (imageBytesToServe == null) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_TEXT_HTML, "Image bytes null");
            }
            cleanupImageStream();
            imageInputStream = new ByteArrayInputStream(imageBytesToServe);
            Log.i(TAG, "Serving image bytes: " + imageBytesToServe.length);
            return newFixedLengthResponse(Response.Status.OK, MIME_IMAGE_PNG, imageInputStream, imageBytesToServe.length);
        }

        private void skipFully(FileInputStream stream, long bytesToSkip) throws IOException {
            long remaining = bytesToSkip;
            while (remaining > 0) {
                long skipped = stream.skip(remaining);
                if (skipped <= 0) {
                    if (stream.read() == -1) {
                        break;
                    }
                    skipped = 1;
                }
                remaining -= skipped;
            }
            if (remaining > 0) {
                throw new IOException("Unable to skip " + bytesToSkip + " bytes");
            }
        }
    }

    void cleanupAudioStream() {
        if (audioInputStream != null) {
            try {
                audioInputStream.close();
            } catch (IOException ignored) {
                // Closing the audio stream failed; nothing further can be done.
            }
        }
    }

    void cleanupImageStream() {
        if (imageInputStream != null) {
            try {
                imageInputStream.close();
            } catch (IOException ignored) {
                // Closing the image stream failed; nothing further can be done.
            }
        }
    }

    private final Map<String, String> mimeTypes = createMimeTypes();

    String getMimeType(String filePath) {
        return mimeTypes.get(filePath.substring(filePath.lastIndexOf(".") + 1));
    }

    private static Map<String, String> createMimeTypes() {
        Map<String, String> mimeTypes = new HashMap<>();
        mimeTypes.put("css", "text/css");
        mimeTypes.put("htm", MIME_TEXT_HTML);
        mimeTypes.put("html", MIME_TEXT_HTML);
        mimeTypes.put("xml", "text/xml");
        mimeTypes.put("java", "text/x-java-source, text/java");
        mimeTypes.put("md", MIME_TEXT_PLAIN);
        mimeTypes.put("txt", MIME_TEXT_PLAIN);
        mimeTypes.put("asc", MIME_TEXT_PLAIN);
        mimeTypes.put("gif", "image/gif");
        mimeTypes.put("jpg", "image/jpeg");
        mimeTypes.put("jpeg", "image/jpeg");
        mimeTypes.put("png", MIME_IMAGE_PNG);
        mimeTypes.put("mp3", "audio/mpeg");
        mimeTypes.put("m3u", "audio/mpeg-url");
        mimeTypes.put("mp4", "video/mp4");
        mimeTypes.put("ogv", "video/ogg");
        mimeTypes.put("flv", "video/x-flv");
        mimeTypes.put("mov", "video/quicktime");
        mimeTypes.put("swf", "application/x-shockwave-flash");
        mimeTypes.put("js", "application/javascript");
        mimeTypes.put("pdf", "application/pdf");
        mimeTypes.put("doc", "application/msword");
        mimeTypes.put("ogg", "application/x-ogg");
        mimeTypes.put("zip", MIME_APPLICATION_OCTET_STREAM);
        mimeTypes.put("exe", MIME_APPLICATION_OCTET_STREAM);
        mimeTypes.put("class", MIME_APPLICATION_OCTET_STREAM);
        return mimeTypes;
    }
}