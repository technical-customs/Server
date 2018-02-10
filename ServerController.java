package GameServer;

import java.awt.event.*;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import pairmap.Pair;
import pairmap.PairMap;



class ServerController{
    
    private final Server server;
    private final ServerGui gui;
    
    public ServerController(Server server, ServerGui gui){
        this.server = server;
        this.gui = gui;
        this.gui.addWindowListener(new WindowAdapter(){
            @Override
            public void windowClosing(WindowEvent we){
                if(shutDownOptionPane((JFrame) gui)){
                    try{
                        disconnectServer();
                    }catch(Exception ex){}
                    System.exit(0);
                }
            }
        });
        
        gui.serverOnButtonListener(new ServerOnAction());
        gui.serverOffButtonListener(new ServerOffAction());
        gui.portConnectButtonListener(new PortConnectAction());
        gui.portDisconnectButtonListener(new PortDisconnectAction());
        
        gui.sendButtonListener(new SendButtonAction());
        gui.clearButtonListener(new ClearButtonAction());
        gui.deleteLogButtonListener(new DeleteLogButtonAction());
        
        gui.userSelectionListener(new UserSelectAction());
        gui.kickUserButtonListener(new KickUserAction());
        gui.kickAllButtonListener(new KickAllAction());
        
        
    }
    
    private boolean shutDownOptionPane(JFrame frame){
        Object[] serverShutDownOptions = {"CONTINUE...", "ABORT!"};
        int serverShutDownOption =  JOptionPane.showOptionDialog(frame,
                "IT IS ADVISED TO NOT SHUTDOWN THE SERVER!",
                "SERVER SHUTDOWN",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                serverShutDownOptions,
                serverShutDownOptions[1]
        );
        
        if(serverShutDownOption == JOptionPane.YES_OPTION){
            return true;
        }
        if(serverShutDownOption == JOptionPane.NO_OPTION){
            return false;
        }
        if(serverShutDownOption == JOptionPane.CLOSED_OPTION){
            return false;
        }
        return false;
    }
    
    
    //**********GUI TO SERVER****************//
    private int getPortNumber(){
        return gui.getPortNumber();
    }
    protected void writeToDisplay(String string){
        gui.writeToDisplay(string);
    }
    //**********END GUI TO SERVER*************//
    
    
    //***********SERVER TO GUI***************//
    private String getIpAddress(){
        return server.getServerAddress();
    }
    
    private synchronized PairMap<SocketChannel,Pair<String, Pair<String, String>>> getusermap(){
        return server.getUserMap();
    }
    
    private synchronized boolean isServerClosed(){
        return server.isServerClosed();
    }
    private synchronized void disconnectServer(){
        server.serverDisconnect();
    }
    
    private synchronized void checkForUsers(){
        new Thread(new Runnable(){
            @Override
            public void run(){
                try{
                    //synchronized(getusermap()){
                        while(server.isServerConnected()){
                            Thread.sleep(100);
                            
                            try{
                            gui.getUserList().setModel(gui.getUserListModel());
                            gui.enableUserClick(gui.getUserListModel().size() > 0);
                            gui.enableKickAllButton(!gui.getUserListModel().isEmpty());
                            
                            if(getusermap().getKeys().size() == gui.getUserListModel().size()){
                                continue;
                            }
                            clearUserList();
                            for(int x = 0; x < getusermap().getPairs().size(); x++){
                                Pair<SocketChannel, Pair<String, Pair<String, String>>> pair = getusermap().getPairs().get(x);
                            
                                
                                    gui.addListItem(pair.getV().getK()+ " - " + pair.getV().getV().getK());
                                
                                
                            }
                            }catch(Exception ex){}
                        }
                        
                    //}
                }catch(Exception ex){
                    System.err.println("User Sync exception: " + ex);
                    server.log("User Sync exception: " + ex);
                }
            }
        }, "User Check Thread").start();
    }
    
    private void clearUserList(){
        gui.clearListModel();
        gui.getUserList().setModel(gui.getUserListModel());
        
    }
    
    //**********END SERVER TO GUI************//
    
    //****************CONTROLLER INFO***********//
    
    private void logGrabber(){
        new Thread(new Runnable(){
            @Override
            public void run(){
                if(!server.getConnected()){
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(ServerController.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                BufferedReader br = null;
                try {
                    //clear Screen
                    gui.clearDisplay();
                    br = new BufferedReader(new InputStreamReader(new FileInputStream(server.getLogFile())));
                    System.out.println("LOG FILE: " + server.getLogFile());
                    
                    while(server.getConnected()){
                        String line = br.readLine();
                        if(line != null){
                            gui.writeToDisplay(line);
                        }else{
                            Thread.sleep(1);
                        }
                        //get log file and update every 60 frames
                        //Thread.sleep(100);
                        
                    }
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(ServerController.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException | InterruptedException ex) {
                    Logger.getLogger(ServerController.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    try {
                        if(br != null){
                            br.close();
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(ServerController.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }, "Log Grabber Thread").start();
    } 
    //************END CONTROLLER INFO***********//
    
    //**************ACTION CLASSES****************//
    class ServerOnAction implements ActionListener{
        @Override
        public void actionPerformed(ActionEvent ae){
            
            gui.enableAll();
            gui.writeToDisplay("SERVER INITIATED" + "\n");
            server.log("SERVER INITIATED");
        }
    }
    class ServerOffAction implements ActionListener{
        @Override
        public void actionPerformed(ActionEvent ae){
            try{
                if(server.getConnected()){
                    disconnectServer();
                }
            }catch(Exception ex){
                System.err.println("Server off Exception:" + ex);
                server.log("Server off Exception:" + ex);
            }finally{
                gui.writeToDisplay("SERVER CLOSED" + "\n");
                server.log("SERVER CLOSED");
                gui.setTitle("-");
                gui.disableAll();
            }
        }
    }
    class PortConnectAction implements ActionListener{
        @Override
        public void actionPerformed(ActionEvent ae){
            SwingWorker<Void,Void> sw = new SwingWorker(){
                @Override
                protected Object doInBackground() throws Exception {
                    try{
                        server.serverConnect(getPortNumber());

                        if(server.getConnected()){
                            publish();
                        }
                    }catch(Exception ex){
                        System.err.println("Server Connect ex: " + ex);
                        server.log("Server Connect ex: " + ex);
                    }
                    return null;
                }
                @Override
                protected void process(List chunks){
                    gui.setTitle(getIpAddress() + " Port: " + getPortNumber());
                    gui.enablePortEditing(false);
                    gui.writeToDisplay("CONNECTED. LISTENING ON " + getIpAddress() + " Port: " + getPortNumber() + "\n");
                    server.log("CONNECTED. LISTENING ON " + getIpAddress() + " Port: " + getPortNumber());
                            
                    //Init here............
                    logGrabber();
                    checkForUsers();
                    gui.enableTextEditing(true);
                }
                
            };
            sw.execute();
        }
    }
    class PortDisconnectAction implements ActionListener{
        @Override
        public void actionPerformed(ActionEvent ae){
            SwingWorker<Void,Void> sw = new SwingWorker(){
                @Override
                protected Object doInBackground() throws Exception {
                    try{
                        disconnectServer();

                        if(isServerClosed()){
                            publish();
                        }
                    }catch(Exception ex){
                        System.err.println("Server Disconnect ex: " + ex);
                        server.log("Server Disconnect ex: " + ex);
                    }
                    return null;
                }
                @Override
                protected void process(List chunks){
                    gui.enablePortEditing(true);
                    gui.enableTextEditing(false);

                    gui.setTitle("-");
                    clearUserList();

                    gui.writeToDisplay("DISCONNECTED" + "\n");
                    server.log("DISCONNECTED");
                }
                
            };
            sw.execute();
        }
    }
    class SendButtonAction implements ActionListener{

        @Override
        public void actionPerformed(ActionEvent ae) {
            try{
            if(!gui.getEnteredText().isEmpty()){
                server.log("SERVER: " + gui.getEnteredText());
                server.broadcastMessage(gui.getEnteredText());
                gui.clearEnteredTextArea();
            }
            }catch(Exception ex){}
        }
    }
    class ClearButtonAction implements ActionListener{
        @Override
        public void actionPerformed(ActionEvent ae) {
            if(gui.getEnteredText() != null){
                gui.clearEnteredTextArea();
            }
        }
    }
    class DeleteLogButtonAction implements ActionListener{

        @Override
        public void actionPerformed(ActionEvent ae) {
        
        }
        
    }
    class UserSelectAction implements ListSelectionListener{

        @Override
        public void valueChanged(ListSelectionEvent e) {
            JList source = (JList)e.getSource();
            try{
               
                if(!e.getValueIsAdjusting()){
                   
                   String sel = source.getSelectedValue().toString();
                   System.out.println("SEL - " + sel);
                    if(!sel.isEmpty()){
                        
                        gui.enableKickButton(true);
                    }else{
                        gui.enableKickButton(false);
                    }
                }
               gui.enableKickButton(false);
               
               
            }catch(Exception ex){
               source.clearSelection();
               System.err.println("USER SELECT ex: " + ex);
               server.log("USER SELECT ex: " + ex);
           }
           
        }
    
}
    class KickUserAction implements ActionListener{
        @Override
        public void actionPerformed(ActionEvent ae){
            //get selecteed user from list
            //gui.getUserListModel().get
            //check if online, boot
            String su = gui.getUserList().getSelectedValue();
            System.out.println("SU - " + su);
            if(su != null){
                Iterator<Pair<SocketChannel, Pair<String, Pair<String, String>>>> uli = getusermap().getPairs().iterator();
                
                while(uli.hasNext()){
                    Pair<SocketChannel, Pair<String, Pair<String, String>>> p = uli.next();
                    
                    if(su.startsWith(p.getV().getK())){
                        System.out.println("Kick " + p.getV().getK());
                        server.log("Kick " + p.getV().getK());
                        
                        try {
                            server.closeUser(p.getK());
                            gui.getUserListModel().removeElement(su);
                            gui.getUserList().setModel(gui.getUserListModel());
                            uli.remove();
                            
                        } catch (IOException ex) {}
                    }
                }
            }
            
        }
    }
    class KickAllAction implements ActionListener{

        @Override
        public void actionPerformed(ActionEvent ae) {
            
                Iterator<SocketChannel> ui = getusermap().getKeys().iterator();

                while(ui.hasNext()){
                    try {
                        SocketChannel sc = ui.next();

                        server.closeUser(sc);
                    } catch (IOException ex) {
                        Logger.getLogger(ServerController.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            
            
        }
        
    }
    //**************END ACTION CLASSES*************//
    
    
    public static void main(String[] arg){
        
    }
}