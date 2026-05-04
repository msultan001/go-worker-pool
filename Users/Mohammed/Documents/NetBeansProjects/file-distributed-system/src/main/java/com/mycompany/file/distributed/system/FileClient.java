package com.mycompany.file.distributed.system;

import java.io.*;
import java.net.*;
import java.util.Scanner;

// client for file talk
public class FileClient {

    private static final String SERVER_IP   = "localhost";
    private static final int    SERVER_PORT = 9090;
    private static final int    BUFFER_SIZE = 8192; 

    public static void main(String[] args) {

        System.out.println("[Client] Connecting to " + SERVER_IP + ":" + SERVER_PORT + "...");

        try (
            Socket           socket  = new Socket(SERVER_IP, SERVER_PORT);
            DataInputStream  in      = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out     = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            Scanner          scanner = new Scanner(System.in)
        ) {
            // read welcome
            System.out.println("[Server] " + in.readUTF());

            while (true) {
                System.out.println("\n=== MENU ===");
                System.out.println("1. LIST");
                System.out.println("2. UPLOAD");
                System.out.println("3. DOWNLOAD");
                System.out.println("4. DELETE");
                System.out.println("5. EXIT");
                System.out.print("Choose: ");

                String input = scanner.nextLine().trim();

                switch (input) {
                    case "1" -> handleList(out, in);
                    case "2" -> handleUpload(scanner, out, in);
                    case "3" -> handleDownload(scanner, out, in);
                    case "4" -> handleDelete(scanner, out, in);
                    case "5" -> {
                        System.out.println("[Client] Exiting. Goodbye!");
                        return;
                    }
                    default  -> System.out.println("[Client] Invalid choice. Enter 1–5.");
                }
            }

        } catch (ConnectException e) {
            System.err.println("[Client] Could not connect to server at "
                    + SERVER_IP + ":" + SERVER_PORT + ". Is the server running?");
        } catch (IOException e) {
            System.err.println("[Client] Connection error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // list files
    private static void handleList(DataOutputStream out, DataInputStream in) throws IOException {
        out.writeUTF("LIST");
        out.flush();

        String response = in.readUTF();
        System.out.println("\n" + response);
    }

    // send file to server
    private static void handleUpload(Scanner scanner, DataOutputStream out, DataInputStream in) throws IOException {

        System.out.print("Enter local file path: ");
        String path = scanner.nextLine().trim();

        File file = new File(path);

        if (!file.exists() || !file.isFile()) {
            System.out.println("[Client] File not found: " + path);
            return;
        }

        System.out.print("Enter name to store on server [" + file.getName() + "]: ");
        String serverName = scanner.nextLine().trim();
        if (serverName.isEmpty()) {
            serverName = file.getName();
        }

        // tell name
        out.writeUTF("UPLOAD " + serverName);
        out.flush();

        // wait ready
        String response = in.readUTF();
        if (!response.equals("READY")) {
            System.out.println("[Server] " + response);
            return;
        }

        // send size and bytes
        out.writeLong(file.length());

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long sent = 0;
            long total = file.length();

            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                sent += bytesRead;
                printProgress("Uploading", sent, total);
            }
        }

        out.flush();
        // big space
        System.out.println(); 

        // check if good
        String result = in.readUTF();
        System.out.println("[Server] " + result);
    }

    // get file from server
    private static void handleDownload(Scanner scanner, DataOutputStream out, DataInputStream in) throws IOException {

        System.out.print("Enter file name to download: ");
        String fileName = scanner.nextLine().trim();

        System.out.print("Save as [downloaded_" + fileName + "]: ");
        String saveName = scanner.nextLine().trim();
        if (saveName.isEmpty()) {
            saveName = "downloaded_" + fileName;
        }

        out.writeUTF("DOWNLOAD " + fileName);
        out.flush();

        String response = in.readUTF();

        if (response.startsWith("ERROR")) {
            System.out.println("[Server] " + response);
            return;
        }

        if (!response.equals("START")) {
            System.out.println("[Server] Unexpected response: " + response);
            return;
        }

        // get size
        long fileSize = in.readLong();
        System.out.printf("[Client] Receiving %s (%d bytes)...%n", fileName, fileSize);

        File dest = new File(saveName);

        try (FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long remaining = fileSize;
            long received  = 0;

            while (remaining > 0) {
                int toRead    = (int) Math.min(buffer.length, remaining);
                int bytesRead = in.read(buffer, 0, toRead);
                if (bytesRead == -1) break;
                fos.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
                received  += bytesRead;
                printProgress("Downloading", received, fileSize);
            }
        }

        // more space
        System.out.println(); 
        System.out.println("[Client] Download complete → " + dest.getAbsolutePath());
    }

    // kill file
    private static void handleDelete(Scanner scanner, DataOutputStream out, DataInputStream in) throws IOException {

        System.out.print("Enter file name to delete on server: ");
        String fileName = scanner.nextLine().trim();

        System.out.print("Are you sure you want to delete \"" + fileName + "\"? (yes/no): ");
        String confirm = scanner.nextLine().trim();

        if (!confirm.equalsIgnoreCase("yes")) {
            System.out.println("[Client] Delete cancelled.");
            return;
        }

        out.writeUTF("DELETE " + fileName);
        out.flush();

        String response = in.readUTF();
        System.out.println("[Server] " + response);
    }

    // show how fast
    private static void printProgress(String label, long done, long total) {
        if (total <= 0) return;
        int pct   = (int) (done * 100 / total);
        int bars  = pct / 5;
        String bar = "█".repeat(bars) + "░".repeat(20 - bars);
        System.out.printf("\r[Client] %s: [%s] %3d%%  (%d / %d bytes)",
                label, bar, pct, done, total);
    }
}
