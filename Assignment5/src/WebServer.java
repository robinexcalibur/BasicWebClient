import javax.swing.text.html.HTMLDocument;
import java.io.*;
import java.net.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Date;
import java.util.Scanner;

class WebServer 
{
    // this is the port the web server listens on
    private static final int PORT_NUMBER = 8080;

    // main entry point for the application
    public static void main(String args[]) 
    {
        try 
        {
            // open socket
            ServerSocket serverSocket = new ServerSocket(PORT_NUMBER);

            // start listener thread
            Thread listener = new Thread(new SocketListener(serverSocket));
            listener.start();

            // message explaining how to connect
            System.out.println("To connect to this server via a web browser, try \"http://127.0.0.1:8080/{url to retrieve}\"");

            // wait until finished
            System.out.println("Press enter to shutdown the web server...");
            Console cons = System.console(); 
            String enterString = cons.readLine();

            // kill listener thread
            listener.interrupt();

            // close the socket
            serverSocket.close();
        } 
        catch (Exception e) 
        {
            System.err.println("WebServer::main - " + e.toString());
        }
    }
}

class SocketListener implements Runnable 
{
    private ServerSocket serverSocket;

    public SocketListener(ServerSocket serverSocket)   
    {
        this.serverSocket = serverSocket;
    }

    // this thread listens for connections, launches a seperate socket connection
    //  thread to interact with them
    public void run() 
    {
        while(!this.serverSocket.isClosed())
        {
            try
            {
                Socket clientSocket = serverSocket.accept();
                Thread connection = new Thread(new SocketConnection(clientSocket));
                connection.start();
                Thread.yield();
            }
            catch(IOException e)
            {
                if (!this.serverSocket.isClosed())
                {
                    System.err.println("SocketListener::run - " + e.toString());
                }
            }
        }
    }
}

class SocketConnection implements Runnable {
    private final String HTTP_LINE_BREAK = "\r\n";

    private Socket clientSocket;

    public SocketConnection(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    // one of these threads is spawned and used to talk to each connection
    public void run() {
        try {
            BufferedReader request = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
            PrintWriter response = new PrintWriter(this.clientSocket.getOutputStream(), true);
            this.handleConnection(request, response);
        } catch (IOException e) {
            System.err.println("SocketConnection::run - " + e.toString());
        }
    }

    //implemented mu HTTP protocol for this server within this method
    private void handleConnection(BufferedReader request, PrintWriter response) {
        try {
            /*
             *
             * implemented my web server here
             *
             * Tasks:
             * ------
             * 1.) Figure out how to parse the HTTP request header
             * 2.) Figure out how to open files
             * 3.) Figure out how to create an HTTP response header
             * 4.) Figure out how to return resources (i.e. files)
             *
             */


            // EXAMPLE: code prints the web request
            String message = this.readHTTPHeader(request);
            System.out.println("Message:\r\n" + message);


            String file = htmlToReturn(message);

            String header = headerBuilder(message);

            //System.out.println(header + file); //here for testing

            if (message.startsWith("GET")) {
                response.println(header + file);
            } else if (message.startsWith("HEAD")) {
                response.println(header);
            } else { //useful for expanding later, for now let's just post the whole thing
                response.println(header + file);
            }

            // close the socket, no keep alives
            this.clientSocket.close();
        } catch (IOException e) {
            System.err.println("SocketConnection::handleConnection: " + e.toString());
        }
    }

    private String readHTTPHeader(BufferedReader reader) {
        String message = "";
        String line = "";
        while ((line != null) && (!line.equals(this.HTTP_LINE_BREAK))) {
            line = this.readHTTPHeaderLine(reader);
            message += line;
        }
        return message;
    }

    private String readHTTPHeaderLine(BufferedReader reader) {
        String line = "";
        try {
            line = reader.readLine() + this.HTTP_LINE_BREAK;
        } catch (IOException e) {
            System.err.println("SocketConnection::readHTTPHeaderLine: " + e.toString());
            line = "";
        }
        return line;
    }

    //this method will take the HTTP request, parse it, and then return the needed HTML file in a string for the main method to return.
    private String htmlToReturn(String message) {
        //This was used to help figure out what classes to use: http://www.java67.com/2016/08/how-to-read-text-file-as-string-in-java.html
        Scanner scanner = new Scanner(message);
        String firstLine = "";

        //break up the first line of the message
        if (scanner.hasNextLine()) {
            firstLine = scanner.nextLine();
        }
        String[] firstLineBroken = firstLine.split("\\s+");


        String file = "";
        String requestHTML = "";

        //build the first requestHTML, account for links
        if (!firstLineBroken[1].startsWith("/webroot") && !firstLineBroken[1].startsWith("webroot")) {
            requestHTML = "webroot" + firstLineBroken[1];
        } else {
            requestHTML = firstLineBroken[1].replaceFirst("/", "");
        }


        if (!requestHTML.endsWith(".html") && !requestHTML.endsWith(".txt")) { //if it's just a directory

            if (Files.exists(Paths.get(requestHTML + "/index.html"))) { //if the index exists, we'll grab that to build into a file
                requestHTML = requestHTML + "/index.html";
                //System.out.println(requestHTML); //for testing
                try {
                    file = new String(Files.readAllBytes(Paths.get(requestHTML)));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return file;
            } else if (Files.isDirectory(Paths.get(requestHTML))) { //double check the directory exists
                return directoryBuilder(requestHTML, firstLineBroken[1]);
            } else { //error
                return errorBuilder(firstLineBroken[1]);
            }
        }


        return null; //if all else fails

    }

    private String headerBuilder(String message) {
        String header = "";
        Scanner scanner = new Scanner(message);
        String firstLine = "";

        //break up the first line of the message
        if (scanner.hasNextLine()) {
            firstLine = scanner.nextLine();
        }
        String[] firstLineBroken = firstLine.split("\\s+");

        header = firstLineBroken[2] + " 200 OK\r\n";

        //MIME types
        if (firstLineBroken[1].endsWith(".txt")) {
            header = header + "Content-Type: text/plain" + "\r\n";
        } else if (firstLineBroken[1].endsWith(".html")) {
            header = header + "Content-Type: text/html" + "\r\n";
        } else { //means the message has been built
            header = header + "Content-Type: text/html" + "\r\n";
        }

        header = header + "\r\n";


        return header;

    }

    private String directoryBuilder (String requestHTML, String previousDirectory) {
        try {
            //used for example on how to use DirectoryStream: https://www.concretepage.com/java/jdk7/example-directorystream-java-nio-file
            // and this: https://docs.oracle.com/javase/7/docs/api/java/nio/file/Files.html#newDirectoryStream
            DirectoryStream<Path> directory = Files.newDirectoryStream(Paths.get(requestHTML));

            String returnedRequest = "<html>\n" +
                    "    <head>\n" +
                    "        <title>" + previousDirectory.replace("/", "").replace("webroot", "") + "</title>\n" +
                    "    </head>\n" +
                    "    <body>\n";

            for (Path page : directory) {
                //System.out.println(page); //for testing
                String pageURL = page.toString().replace("webroot", "");
                String pageName = page.toString().replace("webroot", "").replace(previousDirectory, "").replace(".txt", "").replace("/", "");
                returnedRequest = returnedRequest + "       <a href=\"" + pageURL + "\">" + pageName + "</a><br>\n";

            }

            returnedRequest = returnedRequest + "   </body>\n</html>";

            return returnedRequest; //we want to break the method here

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private String errorBuilder (String request) {
        return  "<html>\n" +
                "    <head>\n" +
                "        <title>" + request.replace("/", "") + "</title>\n" +
                "    </head>\n" +
                "    <body>\n" +
                "       File not found.\n" +
                "   </body>\n" +
                "</html>";
    }

}
