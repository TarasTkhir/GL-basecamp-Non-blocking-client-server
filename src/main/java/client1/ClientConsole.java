package client1;

import org.apache.logging.log4j.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Date;


public class ClientConsole {

    private static Logger log = LogManager.getLogger(ClientConsole.class);
    static volatile StringBuffer stringBuffer = new StringBuffer();
    private static String encryptKey = "";
    private static final int BUFFER_SIZE = 256;

    private static void threadToReadFromConsole() {

        Runnable runnable = () -> {
            String s = "";
            do {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                try {
                    s = reader.readLine();
                    stringBuffer.append(s);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } while (true);
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    private static void conectionParseMessageAndPing() throws IOException {
        InetSocketAddress socketAddress = new InetSocketAddress("localhost", 8090);
        SocketChannel socketChannel = SocketChannel.open(socketAddress);

        log.info("Connecting to Server on port 8090...");
        boolean resetFlag = true;
        long timestamp = 0;

        do {
            ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
            if (resetFlag) {
                timestamp = new Date().getTime();
                resetFlag = false;
            }
            if (stringBuffer.length() != 0) {
                String messageToXOR = stringBuffer.toString();
                String messageXORed = messageToXOR.substring(0, 3) + addXOREncryption(messageToXOR.
                        substring(3, messageToXOR.length())) + ":*%*%_phase_0_*%%*:";
                byte[] bytes = messageXORed.getBytes();
                writeRead(byteBuffer, bytes, socketChannel);
            } else {
                if (new Date().getTime() - timestamp > 1000) {
                    String ping = "ping";
                    writeRead(byteBuffer, ping.getBytes(), socketChannel);
                    resetFlag = true;
                }
            }

        } while (true);
    }

    private static void writeRead(ByteBuffer byteBuffer, byte[] bytes, SocketChannel socketChannel) throws IOException {
        byteBuffer.put(bytes);
        byteBuffer.flip();
        socketChannel.write(byteBuffer);
        byteBuffer.clear();
        stringBuffer.setLength(0);

        int read = socketChannel.read(byteBuffer);
        String incomingMessage = "";
        while (read > 0) {
            byteBuffer.flip();
            while (byteBuffer.hasRemaining()) {
                incomingMessage += (char) byteBuffer.get();
            }
            byteBuffer.clear();
            read = 0;

        }

        byteBuffer.clear();

        if (!incomingMessage.equals("]")) {
            checkMessage(incomingMessage, socketChannel);
        }
    }

    private static String addXOREncryption(String stringToEncrypt) {

        byte[] bytes = stringToEncrypt.getBytes();

        for (int i = 0; i < bytes.length; i++) {
            encryptKey += (int) (Math.random() * 10);
        }

        byte[] toXOR = encryptKey.getBytes();
        byte[] result = new byte[stringToEncrypt.length()];

        for (int i = 0; i < bytes.length; i++) {
            result[i] = (byte) (bytes[i] ^ toXOR[i % toXOR.length]);
        }

        return new String(result);
    }

    private static String takeOffXOREncryption(String stringToTakeOffEncrypt) {

        byte[] result = stringToTakeOffEncrypt.getBytes();
        byte[] takeOffXOR = encryptKey.getBytes();
        byte[] decodeResult = new byte[result.length];

        for (int i = 0; i < result.length; i++) {

            decodeResult[i] = (byte) (result[i] ^ takeOffXOR[i % takeOffXOR.length]);
        }

        return new String(decodeResult);
    }

    private static void checkMessage(String incomingMessage, SocketChannel socketChannel) {

        if (incomingMessage.contains(":*%*%_phase_0_*%%*:")) {

            ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
            incomingMessage = incomingMessage.replaceAll("\\:\\*%\\*%_phase_0_\\*%%\\*\\:", "");
            String idToSend = incomingMessage.substring(5, 8);
            incomingMessage = incomingMessage.substring(8, incomingMessage.length());
            String encrypted = idToSend + addXOREncryption(incomingMessage) + ":*%*%_phase_1_*%%*:";
            byte[] bytes = encrypted.getBytes();
            try {
                writeRead(byteBuffer, bytes, socketChannel);
            } catch (IOException e) {
                log.error("Problem in Client, encryption phase: _0_");
            }

        } else if (incomingMessage.contains(":*%*%_phase_1_*%%*:")) {

            ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
            incomingMessage = incomingMessage.replaceAll("\\:\\*%\\*%_phase_1_\\*%%\\*\\:", "");
            String idToSend = incomingMessage.substring(5, 8);
            incomingMessage = incomingMessage.substring(8, incomingMessage.length());

            String decoded = idToSend + takeOffXOREncryption(incomingMessage) + ":*%*%_phase_2_*%%*:";
            byte[] bytes = decoded.getBytes();
            try {
                writeRead(byteBuffer, bytes, socketChannel);
            } catch (IOException e) {
                log.error("Problem in Client, encryption phase: _1_");
            }


        } else if (incomingMessage.contains(":*%*%_phase_2_*%%*:")) {

            incomingMessage = incomingMessage.replaceAll("\\:\\*%\\*%_phase_2_\\*%%\\*\\:", "");
            String idFrom = incomingMessage.substring(0, 8);
            incomingMessage = incomingMessage.substring(8, incomingMessage.length());
            System.out.println(idFrom + takeOffXOREncryption(incomingMessage));
            encryptKey = "";
        } else System.out.println(incomingMessage);
    }

    public static void main(String[] args) throws IOException {

        threadToReadFromConsole();
        conectionParseMessageAndPing();

    }

}