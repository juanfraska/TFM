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
import java.util.Enumeration;
import java.util.Vector;
import javax.bluetooth.RemoteDevice;
/**
 *
 * @author parallels
 */
public class Hebra_LOS extends Thread {
    
    //Información base de datos
    
    String url = "jdbc:mysql://localhost:3306/tfm_db";
    String user = "root";
    String pass = "aspire5920";
    
    //Vectores con los dispositivos previamente y actualmente descubiertos
    
    private static Vector devicesDiscovered = new Vector(); //Vector de dispositivos descubiertos
    private static Vector lastDiscovered = new Vector(); //Vector de dispositivos previamente descubiertos
    
    public Hebra_LOS(Vector v1,Vector v2){
        
        devicesDiscovered = (Vector) v1.clone();
        lastDiscovered = (Vector) v2.clone();
    }
    public  void run() {
        try {
            /*
            Para cada dispositivo previamente descubierto, buscamos en el nuvevo
            vector de disposistivos encontrados, en el caso en el que no se encuentre
            significa que ha salido de la zona de cobertura y por lo tanto
            debemos de modificar su
            */
            boolean estado;
            String dev1;
            String dev2;
            
            //Conexión para la base de datos
            Connection connection;
            connection =(Connection) DriverManager.getConnection (url,user,pass);
            
            if(!lastDiscovered.isEmpty()){
                //Bucle para dispositivos descubiertos posteriormente
                
                for(Enumeration en = lastDiscovered.elements(); en.hasMoreElements(); ) {
                    //btDevice almacena un dispositivo
                    RemoteDevice btDevice = (RemoteDevice)en.nextElement();
                    dev1 = btDevice.getBluetoothAddress();
                    System.out.println("Dispositivos previamente encontrados "+dev1);
                    estado = false;
                    
                    //Bucle para dispositivos actualmente descubiertos
                    
                    for(Enumeration en2 = devicesDiscovered.elements(); en2.hasMoreElements(); ) {
                        RemoteDevice btDevice2 = (RemoteDevice)en2.nextElement();
                        dev2 = btDevice2.getBluetoothAddress();
                        System.out.println("Dispositivos actualmente encontrados "+dev2);
                        
                        //Si lo encontramos es que sigue en la zona de cobertura
                        if(dev1.equals(dev2)){
                            System.out.println("Dispositivo en la zona de cobertura");
                            estado = true;
                            break;
                        }
                    }
                    
                    //Si no lo encontramos, es que ha salido de la zona de cobertura
                    if (!estado){
                        System.out.println(dev1 + " Fuera de alcance");
                        
                        //Query para actualizar el estado a NLOS
                        String query = "update user set state = ?, discovered_at= ? where address = ?";
                        PreparedStatement preparedStmt = connection.prepareStatement(query);
                        preparedStmt.setString(1, "NLOS");
                        preparedStmt.setTimestamp(2, (Timestamp) getCurrentTimestamp());
                        preparedStmt.setString(3, dev1);
                        
                        System.out.println("Actualizamos estado a NLOS de " + dev1);
                        
                        //Ejecutamos query
                        preparedStmt.executeUpdate();
                    }
                    
                }
            }else System.out.println("Vector vacio");
        } catch (SQLException ex) {
            Logger.getLogger(Hebra_LOS.class.getName()).log(Level.SEVERE, null, ex);
        }
                
        
    }
    private static Date getCurrentTimestamp() {

                java.util.Date today = new java.util.Date();
                return new java.sql.Timestamp(today.getTime());
    }
}
