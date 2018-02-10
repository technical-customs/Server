package GameServer;

import java.io.IOException;
import java.io.Serializable;

public class ServerSetup implements Serializable{
    public static void main(String[] args) throws IOException{
        Server server = new Server();
        ServerGui gui = new ServerGui();
        ServerController c = new ServerController(server,gui);
        
        InfoServer infoserver = new InfoServer();
        ServerGui infogui = new ServerGui();
        infogui.setTitle("InfoServer");
        ServerController infoc = new ServerController(infoserver,infogui);
        
        
    }
}