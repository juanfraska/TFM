/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bt;

//Bluetooth Imports
import static bt.Bt_discoverer.devicesDiscovered;
import java.io.IOException;
import java.util.Vector;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.intel.bluetooth.BlueCoveLocalDeviceProperties;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.*;
//Database Imports
import java.sql.*;
import com.mysql.jdbc.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
//Date Imports
import java.util.Calendar;
import java.util.Date;


public class Bt_discoverer {

    public static Vector devicesDiscovered = new Vector(); //Vector de dispositivos descubiertos
    public static Vector lastDiscovered = new Vector(); //Vector de dispositivos previamente descubiertos
    public static String[] currentlyMACs;    
    public static void main(String[] args) throws BluetoothStateException, InterruptedException, IOException, SQLException {
       
        
        boolean conexion = false;
        
        //Conexión con la base de datos
        String url = "jdbc:mysql://localhost:3306/tfm_db";
        String user = "root";
        String pass = "aspire5920";
        System.out.println("Conectando con la base de datos");
        
        //Hebra que se va a encargar de mostrar los datos
        Hebra_Datos hebra = new Hebra_Datos();

        
                
        
        //Conexión para la base de datos
        Connection connection;
        connection =(Connection) DriverManager.getConnection (url,user,pass);
        
        //Comrobamos la conexión con la base de datos
        if(!connection.isClosed()){
            System.out.println("Conexión con la base de datos correcta");
            
            hebra.url=url;
            hebra.pass=pass;
            hebra.user = user;
            conexion = true;
      
        }
        /*
            Posible mejora: comprobación periodia de que la conexión sigue activa
            o colocar un try/catch con la excepción de desconexión con la base de datos
        */
        
        //Si la conexión con la bd ha sido correcta, buscamos dispositivos
        if(conexion){
            
            //Objeto con el que esperaremos la búsqueda de dispositivos
            final Object completedEvent = new Object();
            devicesDiscovered.clear();
            
            // Dispositivo local
             LocalDevice myBT = LocalDevice.getLocalDevice();
            System.out.println("Local properties | " + " Name: " + myBT.getFriendlyName() + " Addr: "+ myBT.getBluetoothAddress());
            
            //Discovery Agent
            DiscoveryAgent  da= myBT.getDiscoveryAgent();
            //Discovery listener
            DiscoveryListener listener = new MyDiscoveryListener(completedEvent) ;
            
            //Lanzamos el inquiry search constantemente
            while(true){
                
                //Limpiamos el vector
                devicesDiscovered.clear();
                //Lanzamos la inquiry
                da.startInquiry(DiscoveryAgent.LIAC, listener);
                
                //Esperamos a que termine la búsqueda
                synchronized (completedEvent)
                {
                    completedEvent.wait();
                }
                
                Vector currently = (Vector) devicesDiscovered.clone();
                //Hebra para comprobar si se ha perdido la visión de algun dispositivo
                Hebra_LOS hebra_los = new Hebra_LOS(currently,lastDiscovered);
                hebra_los.start();
                
                /*
                    Esperamos a que la hebra termine de comprobar si algún dispositivo
                    ha salido de la zona de cobertura
                */
                try{
                    hebra_los.join();
                }catch(InterruptedException e){
                    e.printStackTrace();
                }
                
                //Limpiamos el vector
                currently.clear();
                
                //Procesamos cada dispositivo
                for(Enumeration en = devicesDiscovered.elements(); en.hasMoreElements(); ) {
                   
                   //btDevice almacena un dispositivo descubierto
                   RemoteDevice btDevice = (RemoteDevice)en.nextElement();
                   
                   /*
                   Comprobamos si no se ha escrito ese usuario en la base de datos
                   Para ello realizasmos una query en busca de su dirección MAC
                   */
                   Statement s= connection.createStatement();
                   String mac_user = btDevice.getBluetoothAddress();
                   String query = "select * from user HAVING address = ";
                   mac_user = "'"+mac_user+"'";
                   query = query.concat(mac_user);
                   ResultSet r = null;
                   try{
                        //Realizamos la query a la bd
                        r = s.executeQuery(query);
                        //En el caso de que no nos devuelva nada, escribimos en la bd
                        if(!r.next()) {
                            //Si no está duplicado, escribimos en la base de datos
                            String query1 = " insert into user (name_user, address,state, discovered_at)"+ " values (?, ?, ?,?)";
                            PreparedStatement preparedStmt;
                            preparedStmt = connection.prepareStatement(query1);
                           
                            //Nombre dispositivo descubierto
                            try {
                                preparedStmt.setString (1, btDevice.getFriendlyName(false));
                            } catch (IOException ex) {
                                Logger.getLogger(MyDiscoveryListener.class.getName()).log(Level.SEVERE, null, ex);
                                preparedStmt.setString (1, "Uknown");
                            }
                            
                            //Dirección MAC dispositivo descubierto
                            preparedStmt.setString (2, btDevice.getBluetoothAddress());
                            
                            //Estado - LOS (Line Of Sight)
                            preparedStmt.setString(3,"LOS");
                            
                            //TimeStamp del dispositivo descubierto
                            preparedStmt.setTimestamp(4, (Timestamp) getCurrentTimestamp());
                            //Ejecutamos la query para añadir al dispositivo en la bd
                            preparedStmt.execute();
                       }else{ 
                        /*
                            Comprobamos si está en el estado NLOS para volver
                            a actualizarlo a LOS ya que lo acabamos de descubrir,
                            también actualizamos el timestamp                  
                        */
                                if(r.getString(4).equals("NLOS")){
                                    
                                    String query2 = "update user set state = ?, discovered_at= ? where address = ?";
                                    PreparedStatement preparedStmt2 = connection.prepareStatement(query2);
                                    preparedStmt2.setString(1, "LOS");
                                    preparedStmt2.setTimestamp(2, (Timestamp) getCurrentTimestamp());
                                    preparedStmt2.setString(3, r.getString(3));
                                    System.out.println("Actualizamos estado a LOS de " + r.getString(3));
                                    preparedStmt2.executeUpdate();
                                }
                        }
                   }catch (SQLException ex) {
                       System.out.println(ex.getMessage());
                   }
                  
                          
                }//end for
                
                lastDiscovered = (Vector) devicesDiscovered.clone();
                
                //Le pasamos el array con las MAC a la hebra
                //hebra.currentlyMACs = currentlyMACs;
                //hebra.start();
                
                
                
            }
        }else{
            System.out.println("No se ha podido conectar con la base de datos");
        }
        
    }
    
    //Función para devolver el timestamp en segundos concreto
    private static Date getCurrentTimestamp() {

                java.util.Date today = new java.util.Date();
                return new java.sql.Timestamp(today.getTime());
    }
    
}



class MyDiscoveryListener implements javax.bluetooth.DiscoveryListener{
            Object completedEvent;
            public MyDiscoveryListener(Object event){
                    completedEvent = event;
            
            }

            public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {  //Device class : clase de dispositivo
                //Añadimos dispositivo a vector (solo dispositivos móviles)
                int class_device = cod.getMajorDeviceClass();
                if(!devicesDiscovered.contains(btDevice) && class_device ==512){
                    devicesDiscovered.addElement(btDevice);
                    //Datos del dispositivo encontrado
                    System.out.println("Direccion Bluetooth " + btDevice.getBluetoothAddress());

                    try {
                        System.out.println("Nombre de dispositivo "+btDevice.getFriendlyName(false));
                    } catch (IOException ex) {
                        Logger.getLogger(MyDiscoveryListener.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                
                
                 
            }

            public void inquiryCompleted(int discType) {
                /*NOTIFICAR LA FINALIZACIÓN DE LA BÚSQUEDA*/
                System.out.println("Device Inquiry completed!");
                System.out.println("Dispositivos encontrados: " + devicesDiscovered.size());
                System.out.println("*********************************");
                synchronized(completedEvent){
                    completedEvent.notifyAll();
                }
            }


            public void serviceSearchCompleted(int transID, int respCode) {}
            public void servicesDiscovered(int transID, ServiceRecord[] rec) {}
            


        };

