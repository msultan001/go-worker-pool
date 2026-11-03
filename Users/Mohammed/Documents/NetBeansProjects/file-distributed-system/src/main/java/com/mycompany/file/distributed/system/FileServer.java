package com.mycompany.file.distributed.system;

import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// server for file talk
public class FileServer {

    static final int    PORT        = 9090;           
    static final String STORAGE_DIR = "server_files";
    static final int    BUFFER_SIZE = 8192;           

    // lock for many people
    static class FileLockManager {
        private static final ConcurrentHashMap<String, ReentrantReadWriteLock> locks =
                new ConcurrentHashMap<>();

        static ReentrantReadWriteLock getLock(String fileName) {
            return locks.computeIfAbsent(fileName, k -> new ReentrantReadWriteLock());
        }
    }

    // start here
    public static void main(String[] args) {

        // make folder if no have
        new File(STORAGE_DIR).mkdirs();

        ExecutorService executor = Executors.newFixedThreadPool(10);

        // stop nice
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Server] Shutting down...");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
            System.out.println("[Server] Stopped.");
        }));

        System.out.println("[Server] TCP File Server started on port " + PORT);
        System.out.println("[Server] Storage directory: " + new File(STORAGE_DIR).getAbsolutePath());

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            serverSocket.setReuseAddress(true);
            // do again

            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] Client connected: " + clientSocket.getInetAddress());
                executor.execute(new ClientHandler(clientSocket));
            }

        } catch (IOException e) {
            System.err.println("[Server] Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // handle client
    static class ClientHandler implements Runnable {

        private final Socket socket;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {

            try (
                DataInputStream  in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))
            ) {
                // say hello
                out.writeUTF("WELCOME: File Server ready. Commands: LIST | UPLOAD <name> | DOWNLOAD <name> | DELETE <name>");
                out.flush();

                String command;

                while (true) {
                    try {
                        command = in.readUTF();
                    } catch (EOFException e) {
                        // client go away
                        break;
                    }

                    System.out.println("[Server] Received from " + socket.getInetAddress() + ": " + command);

                    if (command.equalsIgnoreCase("LIST")) {
                        handleList(out);

                    } else if (command.startsWith("UPLOAD ")) {
                        String fileName = command.substring(7).trim();
                        handleUpload(fileName, in, out);

                    } else if (command.startsWith("DOWNLOAD ")) {
                        String fileName = command.substring(9).trim();
                        handleDownload(fileName, in, out);

                    } else if (command.startsWith("DELETE ")) {
                        String fileName = command.substring(7).trim();
                        handleDelete(fileName, out);

                    } else {
                        out.writeUTF("ERROR: Unknown command: " + command);
                        out.flush();
                    }
                }

            } catch (IOException e) {
                System.err.println("[Server] Client error: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
                System.out.println("[Server] Client disconnected: " + socket.getInetAddress());
            }
        }

        // list files
        private void handleList(DataOutputStream out) throws IOException {
            File dir = new File(STORAGE_DIR);
            System.out.println(dir);
            File[] files = dir.listFiles();

            if (files == null || files.length == 0) {
                out.writeUTF("LIST_RESULT: (no files)");
            } else {
                StringBuilder sb = new StringBuilder("LIST_RESULT:");
                for (File f : files) {
                    sb.append("\n  - ").append(f.getName())
                      .append("  [").append(f.length()).append(" bytes]");
                }
                out.writeUTF(sb.toString());
            }
            out.flush();
        }

        // client send file
        private void handleUpload(String fileName, DataInputStream in, DataOutputStream out) throws IOException {

            // check if bad path
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                out.writeUTF("ERROR: Invalid file name");
                out.flush();
                return;
            }

            ReentrantReadWriteLock lock = FileLockManager.getLock(fileName);
            lock.writeLock().lock();

            try {
                out.writeUTF("READY");
                out.flush();

                long fileSize = in.readLong();

                if (fileSize < 0) {
                    out.writeUTF("ERROR: Invalid file size");
                    out.flush();
                    return;
                }

                File dest = new File(STORAGE_DIR + File.separator + fileName);

                try (FileOutputStream fos = new FileOutputStream(dest)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    long remaining = fileSize;

                    while (remaining > 0) {
                        int toRead = (int) Math.min(buffer.length, remaining);
                        int bytesRead = in.read(buffer, 0, toRead);
                        if (bytesRead == -1) break;
                        fos.write(buffer, 0, bytesRead);
                        remaining -= bytesRead;
                    }
                }

                System.out.printf("[Server] Uploaded: %s (%d bytes)%n", fileName, fileSize);
                out.writeUTF("UPLOAD SUCCESS: " + fileName);
                out.flush();

            } catch (IOException e) {
                out.writeUTF("ERROR: Upload failed — " + e.getMessage());
                out.flush();
                throw e;

            } finally {
                lock.writeLock().unlock();
            }
        }

        // client get file
        private void handleDownload(String fileName, DataInputStream in, DataOutputStream out) throws IOException {

            // check if bad path
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                out.writeUTF("ERROR: Invalid file name");
                out.flush();
                return;
            }

            ReentrantReadWriteLock lock = FileLockManager.getLock(fileName);
            lock.readLock().lock();

            try {
                File file = new File(STORAGE_DIR + File.separator + fileName);

                if (!file.exists()) {
                    out.writeUTF("ERROR: FILE NOT FOUND");
                    out.flush();
                    return;
                }

                out.writeUTF("START");
                out.flush();

                // tell size
                out.writeLong(file.length());
                out.flush();

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }

                out.flush();
                System.out.printf("[Server] Downloaded: %s (%d bytes)%n", fileName, file.length());

            } finally {
                lock.readLock().unlock();
            }
        }

        // kill file
        private void handleDelete(String fileName, DataOutputStream out) throws IOException {

            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                out.writeUTF("ERROR: Invalid file name");
                out.flush();
                return;
            }

            ReentrantReadWriteLock lock = FileLockManager.getLock(fileName);
            lock.writeLock().lock();

            try {
                File file = new File(STORAGE_DIR + File.separator + fileName);

                if (!file.exists()) {
                    out.writeUTF("ERROR: FILE NOT FOUND");
                } else if (file.delete()) {
                    System.out.println("[Server] Deleted: " + fileName);
                    out.writeUTF("DELETE SUCCESS: " + fileName);
                } else {
                    out.writeUTF("ERROR: Could not delete file");
                }
                out.flush();

            } finally {
                lock.writeLock().unlock();
            }
        }
    }
}