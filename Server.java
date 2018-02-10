package GameServer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;
import java.util.Scanner;
import pairmap.Pair;
import pairmap.PairMap;

public class Server{
    //display
    
    //logfile
    final private String logfileDir = System.getProperty("user.home")+ "/Desktop/log/";
    final private String logfileName = logfileDir + "log.txt";
    final private File logfile;
    
    private volatile PairMap<SocketChannel, Pair<String,Pair<String,String>>> userMap;
    private String ipAddress;
    private int portNumber;
    private boolean useLocalHost = false;
    private boolean connected = false;
    private ServerSocketChannel server;
    private Selector sSelector;
    
    //implementation specific additions:
    
    public Server() throws IOException{
        userMap = new PairMap<>();
        
        //create display
        
        //create log file
        logfile = new File(logfileDir);
        logfile.mkdir();
    }
    public Server(int portnumber) throws IOException{
        userMap = new PairMap<>();
        
        //create display
        
        //create log file
        logfile = new File(logfileDir);
        logfile.mkdir();
        
        serverConnect(portnumber);
    }
    
    //Server
    public void setLocalHost(boolean activate){
        useLocalHost = activate;
    }
    public synchronized void serverConnect(int portnumber){
        try{
            if(useLocalHost){
                ipAddress = "127.0.0.1";
            }else{
                ipAddress = Inet4Address.getLocalHost().getHostAddress();
            }
            
            log("IP ADDRESS: " + ipAddress);
            log("PORT NUMBER: " + portnumber);
            
            sSelector = Selector.open();
            
            server = ServerSocketChannel.open();
            
            
            server.configureBlocking(false);
            server.socket().bind(new InetSocketAddress(ipAddress, portnumber));
            
            
            SelectionKey socketServerSelectionKey = server.register(this.sSelector, SelectionKey.OP_ACCEPT);
            
            if(!server.isOpen()){
                System.out.println("ERROR CONNECTING TO SERVER");
                log("ERROR CONNECTING TO SERVER");
                return;
            }
            
            System.out.println("SERVER SETUP SUCCESSFUL!!!");
            log("SERVER SETUP SUCCESSFUL!!!");
            
            connected = true;
            keyCheck();
            //searchForUsers();
            //implementation specific inits:
            
            
            
        }catch(IOException ex){
            System.out.println("Server Connect Exception: " + ex);
            log("Server Connect Exception: " + ex);
            serverDisconnect();
            System.exit(0);
        }
    }
    public synchronized void serverAccept(SelectionKey key){
        try {
            ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
            SocketChannel channel = serverChannel.accept();
            channel.configureBlocking(false);
            channel.register(this.sSelector, SelectionKey.OP_READ);
            
            try{
               
                //String username = getAcceptanceString(channel);
                String username = "" + (new Random().nextInt(100)+1);

                //addUserToMap(new Pair<SocketChannel,String>(channel, username));

            }catch(Exception ex){
                System.err.println("Accepting Ex: " + ex);
                log("Accepting Ex: " + ex);
            }
            
        } catch (IOException ex) {}
    }
    private void keyCheck(){
        new Thread(new Runnable(){
            @Override
            public void run(){
                while(connected){
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {}
                    try {
                        sSelector.select();
                    } catch (IOException ex) {}

                    Iterator keys = sSelector.selectedKeys().iterator();

                    while(keys.hasNext()){
                        SelectionKey key = (SelectionKey) keys.next();
                        keys.remove();

                        if(!key.isValid()){
                            continue;
                        }
                        if(key.isAcceptable()){
                            serverAccept(key);
                            
                        }
                        if(key.isReadable()){
                            try {
                                read(key);
                            } catch (IOException ex) {
                                System.out.println("Server Read Exception: " + ex);
                                log("Read Key Close Exception: " + ex);

                                try {
                                    key.channel().close();
                                } catch (IOException ex1) {
                                    System.out.println("Read Key Close Exception: " + ex);
                                    log("Read Key Close Exception: " + ex);
                                }
                                key.cancel();
                            }
                        }
                        searchForUsers();
                    }
                    
                }
            }
        }).start();
    }
    public synchronized void serverDisconnect(){
        if(connected == false){
            return;
        }
        connected = false;
        
        try{
            closeAllUsers();
            System.out.println("Closed Clients");
            log("Closed Clients");
            
        }catch(IOException ex){
            System.out.println("Close Client ex: " + ex);
            log("Close Client ex: " + ex);
        }
        try{
            server.socket().close();
            server.close();
            
            System.out.println("Disconnected Server");
            log("Disconnected Server");
        }
        catch(IOException ex){
            System.out.println("Server Disconnect Exception: " + ex);
            log("Server Disconnect Exception: " + ex);
        }
    }
    public synchronized boolean isServerConnected(){
        return server.isOpen();
    }
    public synchronized boolean isServerClosed(){
        return !server.isOpen();
    }
    public synchronized boolean getConnected(){
        return connected;
    }
    public synchronized String getServerAddress(){
        return ipAddress;
    }
    
    //Clients 
    private void read(SelectionKey key) throws IOException{
        SocketChannel channel = (SocketChannel) key.channel();
        
        int alloc = 1024;
        ByteBuffer buffer = ByteBuffer.allocate(alloc);
        int numRead = channel.read(buffer);
        
        if(numRead == -1){
            channel.close();
            key.cancel();
            System.out.println("Read Key Closed: " + channel.toString());
            log("Read Key Closed: " + channel.toString());
            return;
        }
        
        if(numRead > 0){
            byte[] data = new byte[numRead];
            System.arraycopy(buffer.array(),0,data,0,numRead);
            String string = new String(data);
            
            if(string.contains("USERNAME=") && string.contains("USERNAMEEND")){
                int i = string.indexOf("USERNAME=")+"USERNAME=".length();
                String un = string.substring(i,string.indexOf("USERNAMEEND"));
                string = string.substring(string.indexOf("USERNAMEEND") + "USERNAMEEND".length());
                addUserToMap(new Pair<>(channel, un));
                
            }
            if(string.contains("DEVICENAME=") && string.contains("DEVICENAMEEND")){
                int i = string.indexOf("DEVICENAME=")+"DEVICENAME=".length();
                String un = string.substring(i,string.indexOf("DEVICENAMEEND"));
                string = string.substring(string.indexOf("DEVICENAMEEND") + "DEVICENAMEEND".length());
                
                String[] devpass = un.split(",");
                //System.out.println("SERVERDEVPASS - " + Arrays.toString(devpass));
                try{
                    if(devpass[1].equalsIgnoreCase("null")){
                        addDeviceToMap(channel,devpass[0]," ");
                    }else{
                        addDeviceToMap(channel,devpass[0],devpass[1]);
                    }
                    
                }catch(Exception ex){
                    addDeviceToMap(channel,devpass[0]," ");
                }
            }
            
            if(string.toLowerCase().contains("echo:")){
                int i = string.indexOf("echo:")+"echo:".length();
                String un = string.substring(i);
                broadcastMessage(channel,un);
            }
            if(string.toLowerCase().contains("request all clients")){
                StringBuilder sb = new StringBuilder();
                for(int x = 0; x < userMap.getPairs().size(); x++){
                    Pair<SocketChannel,Pair<String,Pair<String,String>>> pair = userMap.getPairs().get(x);
                    String device = pair.getV().getV().getK();
                    sb.append(pair.getV().getK()).append(" - ").append(device).append("\n");
                }
                write(channel,sb.toString());
                
            }
            
            if(string.contains("DEVICEMESSAGE=") && string.contains("DEVICEMESSAGEEND")){
                int i = string.indexOf("DEVICEMESSAGE=")+"DEVICEMESSAGE=".length();
                
                String un = string.substring(i, string.indexOf("DEVICEMESSAGEEND"));
                
                String dn = un.substring(un.indexOf("D*")+"D*".length(),un.indexOf("N*"));
                String msg = un.substring(un.indexOf("N*")+"N*".length());
                string = string.substring(string.indexOf("DEVICEMESSAGEEND") + "DEVICEMESSAGEEND".length());
                
                for(Pair<SocketChannel, Pair<String, Pair<String,String>>> pair: userMap.getPairs()){
                    if(pair.getV().getV().getK().equalsIgnoreCase(dn)){
                        write(pair.getK(),userMap.get(channel).getV().getK() + " >> " +msg);
                    }
                }
            }
            
            if(string.contains("REMOTECONNECT=") && string.contains("REMOTECONNECTEND")){
                int i = string.indexOf("REMOTECONNECT=")+"REMOTECONNECT=".length();
                String un = string.substring(i,string.indexOf("REMOTECONNECTEND"));
                string = string.substring(string.indexOf("REMOTECONNECTEND") + "REMOTECONNECTEND".length());
                String[] devpass = un.split(",");
                String deviceId = devpass[0];
                 
                for(int x = 0; x < userMap.getPairs().size(); x++){
                    Pair<SocketChannel, Pair<String, Pair<String,String>>> pair = userMap.getPairs().get(x);
                
                    if(pair.getV().getV() == null){
                        continue;
                    }
                    
                    if(pair.getV().getV().getK().equalsIgnoreCase(deviceId)){
                        String pass = pair.getV().getV().getV();
                        if(devpass[1].equals(pair.getV().getV().getV()) || pass.equals(" ")){
                            
                            write(channel,"REMOTECONNECT=true,"+devpass[0]+","+devpass[1]+"REMOTECONNECTEND");
                            write(pair.getK(),"ENABLEREMOTE=trueENABLEREMOTEEND");
                        }
                    }
                }
            }
            
            
            if(string.contains("REQUESTCODE=") && string.contains("REQUESTCODEEND")){
                int i = string.indexOf("REQUESTCODE=")+"REQUESTCODE=".length();
                String un = string.substring(i,string.indexOf("REQUESTCODEEND"));
                string = string.substring(string.indexOf("REQUESTCODEEND") + "REQUESTCODEEND".length());
                
                String[] una = un.split("~");
                
                try{
                for(Pair<SocketChannel, Pair<String, Pair<String,String>>> pair: userMap.getPairs()){
                    if(pair.getV().getV() == null){
                        continue;
                    }
                    if(pair.getV().getV().getK().equalsIgnoreCase(una[0])){//get remote client
                        String pass = pair.getV().getV().getV();
                        
                        if(una[1].equals(pair.getV().getV().getV()) || pass.equals(" ")){
                            write(pair.getK(), "REQUESTCODE="+userMap.get(channel).getV().getK()+"~"+una[2]+"REQUESTCODEEND");
                        }
                    }
                }
                }catch(Exception ex){}
            }
            
            if(string.contains("RESULTCODE=") && string.contains("RESULTCODEEND")){
                int i = string.indexOf("RESULTCODE=")+"RESULTCODE=".length();
                String un = string.substring(i,string.indexOf("RESULTCODEEND"));
                string = string.substring(string.indexOf("RESULTCODEEND") + "RESULTCODEEND".length());
                
                String[] una = un.split("~");
                
                try{
                for(Pair<SocketChannel, Pair<String, Pair<String,String>>> pair: userMap.getPairs()){
                    if(pair.getV().getV().getK().equalsIgnoreCase(una[0])){
                        write(pair.getK(),una[1]);
                    }
                }
                }catch(Exception ex){
                    for(Pair<SocketChannel, Pair<String, Pair<String,String>>> pair: userMap.getPairs()){
                        if(pair.getV().getV().getK().equalsIgnoreCase(una[0])){
                            write(pair.getK()," ");
                        }
                }
                }
                
            }
            
            if(string.contains("TRANSFERFUNDS=") && string.contains("TRANSFERFUNDSEND")){
                int i = string.indexOf("TRANSFERFUNDS=")+"TRANSFERFUNDS=".length();
                String un = string.substring(i,string.indexOf("TRANSFERFUNDSEND"));
                string = string.substring(string.indexOf("TRANSFERFUNDSEND") + "TRANSFERFUNDSEND".length());
                
                String[] una = un.split("~");
                
                //System.out.println("SERVER TRANSFER");
                
                try{
                    int an = Integer.parseInt(una[0]);//account number
                    int ar = Integer.parseInt(una[1]); //account routing
                    String rq = una[2];//requester
                    double funds = Double.parseDouble(una[3]);
                    
                    //System.out.println("AN - " + an);
                    //System.out.println("AR - " + ar);
                    //System.out.println("RQ - " + rq);
                    //System.out.println("FUNDS - " + funds);
                    
                    //System.out.println("INIT TRANSFER FROM "+ userMap.get(channel).getK() + " TO " + an);
                    //log("INIT TRANSFER FROM "+ userMap.get(channel).getK() + " TO " + an);
                    
                    for(Pair<SocketChannel,Pair<String,Pair<String,String>>> pair: userMap.getPairs()){
                        if(!pair.getK().equals(channel)){
                            write(pair.getK(),"ACCOUNTINFO="+an+"~"+ar+"~"+userMap.get(channel).getV().getK()+"~"+funds+"ACCOUNTINFOEND");
                        }
                    }
                }catch(Exception ex){}
            }
            if(string.contains("ACCOUNTINFO=") && string.contains("ACCOUNTINFOEND")){
                int i = string.indexOf("ACCOUNTINFO=")+"ACCOUNTINFO=".length();
                String un = string.substring(i,string.indexOf("ACCOUNTINFOEND"));
                string = string.substring(string.indexOf("ACCOUNTINFOEND") + "ACCOUNTINFOEND".length());
                
                String[] una = un.split("~");
                try{
                    String tord = una[0];
                    String c = una[1];
                    double funds = Double.parseDouble(una[2]);

                    for(Pair<SocketChannel,Pair<String,Pair<String,String>>> pair: userMap.getPairs()){
                        if(pair.getV().getV().getK().equalsIgnoreCase(c)){
                            write(pair.getK(),"TRANSFER="+"true"+"~"+funds+"TRANSFEREND");
                        }
                    }
                }catch(Exception ex){}
            }
            
            
            log("READ STRING FROM: " + userMap.get(channel).getK() + " " + string);
        }
    }
    private void write(SocketChannel channel, String string) throws IOException{
        if(string == null){
            //determine what to do with null object
            string = "HIIIIII";
        }
        if(string.toUpperCase().startsWith("QUIT")){
            serverDisconnect();
            System.exit(0);
        }
        channel.register(this.sSelector, SelectionKey.OP_WRITE);
        
        ByteBuffer buf = ByteBuffer.wrap(string.getBytes());
        buf.put(string.getBytes());
        buf.flip();

        while(buf.hasRemaining()) {
            try {
                channel.write(buf);
            } catch (IOException ex) {
                //System.out.println("Write to key ex: " + ex);
                //log("Write to key ex: " + ex);
                return;
            }
        }
        channel.register(this.sSelector, SelectionKey.OP_READ);
    }
    protected void broadcastMessage(SocketChannel bc, String string){
        if(string.toUpperCase().startsWith("QUIT")){
            //serverDisconnect();
            //System.exit(0);
        }
        if(userMap == null || userMap.getPairs().isEmpty()){
            return;
        }
        
        //pair<sc pair<username pair<deviceid,devicepass
        for(int x = 0; x < userMap.getPairs().size(); x++){
            Pair<SocketChannel, Pair<String, Pair<String, String>>> p = userMap.getPairs().get(x);
        
            if(p.getK().equals(bc)){
                continue;
            }
            System.out.println(userMap.get(bc).getK() +" TO " + p.getV().getK() + ": " + string);
            log(userMap.get(bc).getK() + " TO " + p.getV().getK() + ": " + string);
            
        }
        try{
            Iterator<Pair<SocketChannel, Pair<String, Pair<String, String>>>> uli = userMap.getPairs().iterator();
            while(uli.hasNext()){
                
                Pair<SocketChannel, Pair<String, Pair<String, String>>> p = uli.next();
                if(p.getK().equals(bc)){
                    continue;
                }
                
                write(p.getK(), userMap.get(bc).getK() + ">> " + string);
            }
        }catch(IOException ex){
            System.err.println("Broadcast exception: " + ex);
            log("Broadcast exception: " + ex);
        }
    }
    protected void broadcastMessage(String string){
        if(string.toUpperCase().startsWith("QUIT")){
            serverDisconnect();
            System.exit(0);
        }
        if(userMap == null || userMap.getPairs().isEmpty()){
            return;
        }
        
        try{
            Iterator<Pair<SocketChannel, Pair<String, Pair<String, String>>>> uli = userMap.getPairs().iterator();
            while(uli.hasNext()){
                Pair<SocketChannel, Pair<String, Pair<String, String>>> p = uli.next();
                
                System.out.println("SERVER TO " + p.getV().getK() + ": " + string);
                log("SERVER TO " + p.getV().getK() + ": " + string);
                write(p.getK(),"SERVER- " + string);
            }
        }catch(IOException ex){
            System.err.println("Broadcast exception: " + ex);
            log("Broadcast exception: " + ex);
        }
    }
    
    //UserList
    public void closeUser(SocketChannel s) throws IOException{
        
        Iterator<SocketChannel> userIter = userMap.getKeys().iterator();

        while(userIter.hasNext()){
            SocketChannel sc = userIter.next();
            if(sc == null){
                continue;
            }
            if(s.equals(sc)){
                sc.close();
            }
        } 
    }
    private void closeAllUsers() throws IOException{
        Iterator<SocketChannel> userIter = userMap.getKeys().iterator();
        
        while(userIter.hasNext()){
            SocketChannel sc = userIter.next();
            sc.close();
        }
    }
    public PairMap<SocketChannel, Pair<String,Pair<String,String>>> getUserMap(){
        return this.userMap;
    }
    
    private void addUserToMap(Pair<SocketChannel, String> pair) throws IOException{
        //usermap = <channel, pair<username, pair<devicename, devicepass>
        
        if(pair.getK() == null || pair.getV() == null || pair.getV().isEmpty()){
            return;
        }
        if(userMap.containsKey(pair.getK())){
            return;
        }
        userMap.add(pair.getK(), new Pair<>(pair.getV(), new Pair<>(null,null)));
        log("CHANNEL ADDED TO MAP: " + pair.getK().getLocalAddress() + " USERNAME: " + pair.getV());
    }
    private void addDeviceToMap(SocketChannel channel, String deviceName, String devicePass){
        for(Pair<SocketChannel, Pair<String, Pair<String, String>>> pair: userMap.getPairs()){
            if(channel.equals(pair.getK())){
                pair.getV().getV().setK(deviceName);
                pair.getV().getV().setV(devicePass);
            }
        }
    }
    private void searchForUsers(){
        //new Thread(new Runnable(){
            //@Override
            //public void run(){
                //while(connected){
                    
                    try{
                        if(userMap.getPairs().isEmpty()){
                            return;
                        }
                        for(int x = 0; x < userMap.getPairs().size(); x++){
                            Pair<SocketChannel, Pair<String, Pair<String, String>>> pair = userMap.getPairs().get(x);
                        
                            if(!pair.getK().isOpen()){
                                userMap.remove(pair);
                            }
                        }
                    }catch(Exception ex){
                        System.err.println("Search ex: " + ex);
                    }
                //}
            //}
        //}).start();
    }
    
    private void displayUsers(int time){
        new Thread(new Runnable(){
            @Override
            public void run(){
                int x = 0;
                while(connected){
                    
                    
                    
                    Iterator<SocketChannel> userIter = userMap.getKeys().iterator();
                    if(!userMap.getPairs().isEmpty()){
                        try {
                            if(userIter.hasNext()){
                                SocketChannel sc = userIter.next();
                                System.out.println("User: " + sc.toString() + "Username: " + userMap.get(sc));
                            }
                            Thread.sleep(time*1000);
                        } catch (InterruptedException ex) {}
                    }
                }
            }
        }).start();
    }
    
    public void log(String string){
       
        try(BufferedWriter buff = new BufferedWriter(new FileWriter(logfileName, true));) {
            LocalDateTime now = LocalDateTime.now();
            String stamp = now.format(DateTimeFormatter.ofPattern("MM/dd/yyy h:mm:ss.SSS a"));
            
            buff.append(stamp);
            buff.append(": ");
            buff.append(string);
            buff.newLine();
            buff.flush();  
        } catch (IOException ex) {}
        
    }
    public File getLogFile(){
        if(Files.exists(Paths.get(logfileName))){
            return new File(logfileName);
        }
        return null;
    }
    private void timeout(int time){
        if(time < 0){
            return;
        }
        if(time == 0){
            System.out.println("Disconnect");
            log("DISCONNECT");
            serverDisconnect();
            System.exit(0);
        }
        new Thread(new Runnable(){
            @Override
            public void run(){
                int x = 0;
                while(connected){
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {}
                    x++;

                    if(x == time){
                        System.out.println("Timeout");
                        log("TIMEOUT");
                        serverDisconnect();
                        System.exit(0);
                    }
                }
            }
        }).start();
    }
    public void sInput(){
        //initiate server command line
        new Thread(new Runnable(){
            @Override
            public void run() {
                try{
                    Scanner scanner = new Scanner(System.in);
                    
                    while(scanner.hasNextLine()){
                        String line = scanner.nextLine();
                        System.out.println("SERVER: " + line);
                        log("SERVER: " + line);
                        broadcastMessage(line);
                    }
                }catch(Exception ex){System.err.println(ex);}
            } 
        }).start();
    }
    
    
    //implementation specific methods:
    
   
    
    
    
    
    private String getRemoteClientCode(String deviceId, String code){
        for(Pair<SocketChannel, Pair<String, Pair<String,String>>> pair: userMap.getPairs()){
            if(pair.getV().getV().getK().equalsIgnoreCase(deviceId)){
                
                try {
                    write(pair.getK(), "REQUESTCODE="+code+"REQUESTCODEEND");
                } catch (IOException ex) {}
            }
        }
        return "";
    }
    
    public static void main(String[] args) throws IOException{
        //Display display = new Display();
        Server server = new Server();
        
        try{
            server.serverConnect(3803);
            
            if(server.isServerConnected()){
                System.out.println("Connected");
                
                server.sInput();
                //server.displayUsers(2);
                server.timeout(15);
            }
        }catch(Exception ex){}
        
        
    }
}