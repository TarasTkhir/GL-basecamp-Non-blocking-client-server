package client;

import org.apache.logging.log4j.*;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Date;


public class ClientGUI {

    private static Logger log = LogManager.getLogger(ClientGUI.class);
    static volatile StringBuffer stringBuffer = new StringBuffer();
    private static String encryptKey = "";
    private static final int BUFFER_SIZE = 256;
    private static String result = "";


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
            result = idFrom + takeOffXOREncryption(incomingMessage);
            encryptKey = "";
        } else result = incomingMessage;
    }

    private static void graphicInterface() {

        JTextField textField;
        JTextArea textArea;
        JFrame jFrame = new JFrame("TARAS_CHAT");
        jFrame.setSize(400, 400);
        jFrame.setLocationRelativeTo(null);
        jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        JPanel panel1 = new JPanel();
        panel1.setLayout(new BorderLayout());

        textField = new JTextField();
        panel.add(textField, BorderLayout.CENTER);

        JButton button = new JButton("SEND");
        panel.add(button, BorderLayout.EAST);

        textArea = new JTextArea();
        panel1.add(textArea, BorderLayout.CENTER);
        panel1.add(panel, BorderLayout.SOUTH);

        jFrame.setContentPane(panel1);

        button.addActionListener(ev -> {
                    String send = textField.getText();
                    textField.setText("");
                    stringBuffer.append(send);
                    textArea.append(send + "\n");
                }
        );

        jFrame.setVisible(true);

        new Thread(() -> {

            do {
                String serverMsg = result;
                if ((serverMsg != "") && (serverMsg != null)) {
                    textArea.append(serverMsg + "\n");
                    result = "";
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.error("Client GUI, method graphicInterface() : thread sleep error");
                }
            } while (true);
        }).start();
    }

    public static void main(String[] args) {

        graphicInterface();
        try {
            conectionParseMessageAndPing();
        } catch (IOException e) {
            log.fatal("Client GUI, method: conectionParseMessageAndPing()\n" + e.getStackTrace());
        }

    }

}