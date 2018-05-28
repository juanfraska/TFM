/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bt;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
//Database Imports
import com.mysql.jdbc.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Vector;
/**
 *
 * @author parallels
 */
public class Hebra_Datos extends Thread {
    public String url;
    public String user;
    public String pass;
    public String[] currentlyMACs;
    public  void run() {
       
      /*try(Connection connection = (Connection) DriverManager.getConnection(url, user,pass)){
           System.out.println("Conexión para leer los datos");
           Statement s= connection.createStatement();
           String query = "select * from user";
           ResultSet r = s.executeQuery(query);
           while (r.next()) 
           {    
               
             //System.out.println (r.getInt (1) + " " + r.getString (2)+ " " + r.getString(3) + " " + r.getString(4)+ " "+ r.getDate(5) ); 
           }
      } catch (SQLException ex) {
            Logger.getLogger(Hebra_Datos.class.getName()).log(Level.SEVERE, null, ex);
        }
        */
        for(int i=0; i<currentlyMACs.length;i++){
            System.out.println(currentlyMACs[i]);
        }
    }
}
