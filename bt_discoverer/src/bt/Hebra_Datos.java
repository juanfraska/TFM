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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Vector;
/**
 *
 * @author parallels
 */
public class Hebra_Datos extends Thread {
    String url = "jdbc:mysql://localhost:3306/DB_TFM";
    String user = "root";
    String pass = "aspire5920";
    
    public  void run() {
        Connection connection = null;
        try{
            connection =(Connection) DriverManager.getConnection (url,user,pass);
            System.out.println("Conectado!!");

            // Leemos de la base de datos
            Statement s = connection.createStatement();
            String query2 = "select * from user";
            ResultSet r = s.executeQuery(query2);
            while (r.next()) 
            {
                /*
                    Si el dispositivo está en estado NLOS y comprobamos que ha pasado
                    mas de 10 minutos en ese estado, lo eliminamos de la base de datos
                */
                
                if (r.getString(4).equals("NLOS")){
                    java.sql.Timestamp time_device;
                    //TimeStamp actual
                    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                    //TimeStamp del dispositivo
                    time_device = r.getTimestamp(6); 
                    //Calculamos la diferencia de minutos
                    if(dif_minutes(time_device,timestamp) > 1){
                        System.out.println("Dispositivo "+r.getString(3)+ " fuera de alcance durante mas de x minutos");
                        //Cambiamos estado
                        String query = "update user set state = ? where (address = ? and state = ?) ";
                        PreparedStatement preparedStmt = connection.prepareStatement(query);
                        preparedStmt.setString(1, "OUT");
                        preparedStmt.setString(2, r.getString(2));
                        preparedStmt.setString(3, "NLOS");
                        preparedStmt.executeUpdate();
                    }else{
                        System.out.println("Dispositivo fuera de alcance");
                        System.out.println (r.getInt (1) + " " + r.getString (2)+ " " + r.getString(3) + " "+r.getString(4) + " " +r.getTimestamp(5));
                    }
                }else if(r.getString(4).equals("LOS")){
                    System.out.println("Dispositivo en zona de cobertura");
                    System.out.println (r.getInt (1) + " " + r.getString (2)+ " " + r.getString(3) + " "+r.getString(4) + " " +r.getTimestamp(5));
                }     
            }       
        }catch(SQLException e){
                System.out.println(e.getMessage());
        }finally{
            try{
                if(connection!=null){
                    connection.close();
                }
            }catch(SQLException ex){
                System.out.println(ex.getMessage());
            }
        }
    }
    
 
   private static long dif_minutes(java.sql.Timestamp t1, java.sql.Timestamp t2){
        long milis_t1 = t1.getTime();
        long milis_t2 = t2.getTime();
        long diff = (milis_t2 - milis_t1)/(1000*60);
                
        return (diff);
    }
}

