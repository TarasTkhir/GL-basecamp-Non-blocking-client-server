package server;

import org.apache.logging.log4j.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;


public class Server {

    private static Logger log = LogManager.getLogger(Server.class);
    private static int count = 0;
    private static HashMap<String, String> idToRemote = new HashMap<>();
    private static HashMap<String, String> remoteToId = new HashMap<>();
    private static HashMap<String, String> clientMessage = new HashMap<>();
    private static String idToSend = "";


    public static void main(String[] args) throws IOException {

        Selector selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        InetSocketAddress addressSocket = new InetSocketAddress("localhost", 8090);
        serverSocketChannel.bind(addressSocket);
        serverSocketChannel.configureBlocking(false);

        SelectionKey selectKy = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {

            selector.select();

            Set<SelectionKey> selectorKeys = selector.selectedKeys();
            Iterator<SelectionKey> selectionKeyIterator = selectorKeys.iterator();

            while (selectionKeyIterator.hasNext()) {
                SelectionKey myKey = selectionKeyIterator.next();
                try {
                    if (myKey.isAcceptable()) {
                        acceptClient(selector, serverSocketChannel);
                    } else if (myKey.isReadable()) {
                        readFromClient(myKey, selector);
                    } else if (myKey.isWritable()) {
                        writeToClient(myKey, selector);
                    }
                } catch (Exception e) {

                    idToRemote.remove(remoteToId.remove(((SocketChannel) myKey.channel()).getRemoteAddress().toString()));
                    myKey.cancel();
                    try {
                        myKey.channel().close();
                    } catch (Exception ce) {
                        log.error("In main selectionKey Iteration went wrong");
                        ce.printStackTrace();
                    }
                }
                selectionKeyIterator.remove();
            }
        }
    }

    private static void acceptClient(Selector selector, ServerSocketChannel serverSocketChannel) throws IOException {

        SocketChannel acceptableSocketChanel = serverSocketChannel.accept();
        acceptableSocketChanel.configureBlocking(false);
        acceptableSocketChanel.register(selector, SelectionKey.OP_READ);
        idToSend = getId();
        idToRemote.put(idToSend, acceptableSocketChanel.getRemoteAddress().toString());
        remoteToId.put(acceptableSocketChanel.getRemoteAddress().toString(), idToSend);
        clientMessage.put(idToSend, "you are " + idToSend);
        log.info("Connection Accepted: " + idToRemote + "\n");
    }

    private static void readFromClient(SelectionKey myKey, Selector selector) throws IOException {

        SocketChannel readSocketChannel = (SocketChannel) myKey.channel();
        ByteBuffer byteBuffer = ByteBuffer.allocate(50);
        int intForReading = readSocketChannel.read(byteBuffer);
        String messageFromClient = "";
        while (intForReading > 0) {
            byteBuffer.flip();
            while (byteBuffer.hasRemaining()) {
                messageFromClient += (char) byteBuffer.get();
            }
            byteBuffer.clear();
            intForReading = readSocketChannel.read(byteBuffer);
        }

        byteBuffer.clear();
        if (!messageFromClient.equals("ping")) {
            log.info("Message received: " + messageFromClient);
            idToSend = messageFromClient.substring(0, 3);
            clientMessage.put(idToSend, "from:" + remoteToId.get(readSocketChannel.getRemoteAddress().toString())
                    + messageFromClient.substring(3, messageFromClient.length()));
        }
        if (messageFromClient.equals("ping")) {
            if (clientMessage.get(idToSend).equals("]"))
                clientMessage.put(idToSend, "]");
        }
        readSocketChannel.register(selector, SelectionKey.OP_WRITE);
    }

    private static void writeToClient(SelectionKey myKey, Selector selector) throws IOException {

        SocketChannel writeSocketChannel = (SocketChannel) myKey.channel();
        ByteBuffer byteBuffer = ByteBuffer.allocate(256);
        String messageToClient = "]";
        if (!(idToRemote.get(idToSend) == null) && idToRemote.get(idToSend).
                equals(writeSocketChannel.getRemoteAddress().toString())) {
            messageToClient = clientMessage.get(idToSend);
            if (!messageToClient.equals("]"))
                log.info("Sending message: " + messageToClient);
            clientMessage.put(idToSend, "]");
        }
        byte[] bytes = messageToClient.getBytes();
        byteBuffer.put(bytes);
        byteBuffer.flip();
        writeSocketChannel.write(byteBuffer);
        byteBuffer.clear();
        if (!messageToClient.equals("]"))
            log.info("Message has been sent: " + messageToClient);
        writeSocketChannel.register(selector, SelectionKey.OP_READ);
    }

    private static String getId() {
        String id = "";
        count++;
        return id + (100 + count);
    }
}
