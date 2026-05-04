package com.mycompany.file.distributed.system.ui;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.Consumer;

// net layer for file talk
public class FileClientService {

    private static final int BUFFER_SIZE = 8192;

    private Socket           socket;
    private DataInputStream  in;
    private DataOutputStream out;
    private volatile boolean connected = false;

    // make socket
    // connect now
    public String connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(0); 
        in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        connected = true;
        return in.readUTF();
    }

    public void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    // list files
    public List<FileEntry> listFiles() throws IOException {
        out.writeUTF("LIST");
        out.flush();

        String response = in.readUTF();
        List<FileEntry> entries = new ArrayList<>();

        if (response.startsWith("LIST_RESULT:")) {
            for (String line : response.split("\n")) {
                line = line.trim();
                if (!line.startsWith("- ")) continue;

                String content = line.substring(2); 
                int bStart = content.lastIndexOf('[');
                int bEnd   = content.lastIndexOf(']');

                if (bStart > 0 && bEnd > bStart) {
                    String name    = content.substring(0, bStart).trim();
                    String sizeRaw = content.substring(bStart + 1, bEnd)
                                           .replace(" bytes", "").trim();
                    long size = 0;
                    try { size = Long.parseLong(sizeRaw); } catch (NumberFormatException ignored) {}
                    entries.add(new FileEntry(name, size));
                } else {
                    entries.add(new FileEntry(content.trim(), -1));
                }
            }
        }
        return entries;
    }

    // send file
    public String uploadFile(String serverName, File file,
                             Consumer<Double> onProgress) throws IOException {
        out.writeUTF("UPLOAD " + serverName);
        out.flush();

        String ready = in.readUTF();
        if (!ready.equals("READY")) return ready;

        long fileSize = file.length();
        out.writeLong(fileSize);

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int    bytesRead;
            long   sent = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                sent += bytesRead;
                if (onProgress != null && fileSize > 0) {
                    onProgress.accept((double) sent / fileSize);
                }
            }
        }
        out.flush();
        return in.readUTF();
    }

    // get file
    public String downloadFile(String serverName, File dest,
                               Consumer<Double> onProgress) throws IOException {
        out.writeUTF("DOWNLOAD " + serverName);
        out.flush();

        String response = in.readUTF();
        if (!response.equals("START")) return response; 

        long fileSize = in.readLong();

        try (FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buffer    = new byte[BUFFER_SIZE];
            long   remaining = fileSize;
            long   received  = 0;

            while (remaining > 0) {
                int toRead    = (int) Math.min(buffer.length, remaining);
                int bytesRead = in.read(buffer, 0, toRead);
                if (bytesRead == -1) break;
                fos.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
                received  += bytesRead;
                if (onProgress != null && fileSize > 0) {
                    onProgress.accept((double) received / fileSize);
                }
            }
        }
        return "DOWNLOAD SUCCESS: " + serverName;
    }

    // kill file
    public String deleteFile(String serverName) throws IOException {
        out.writeUTF("DELETE " + serverName);
        out.flush();
        return in.readUTF();
    }
}
