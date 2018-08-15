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
//Other Imports
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;


public class Bt_discoverer {

    public static Vector devicesDiscovered = new Vector(); //Vector de dispositivos descubiertos
    public static Vector lastDiscovered = new Vector(); //Vector de dispositivos previamente descubiertos
    public static String[] currentlyMACs;    
    public static Semaphore mutex = new Semaphore(1);
    
    public static void main(String[] args) throws BluetoothStateException, InterruptedException, IOException, SQLException {
       
        
        boolean conexion = false;
        
        //Conexión con la base de datos
        String url = "jdbc:mysql://localhost:3306/DB_TFM";
        String user = "root";
        String pass = "aspire5920";
        System.out.println("Conectando con la base de datos");
        
        //Hebra que se va a encargar de mostrar los datos
        Hebra_Datos hebra_out = new Hebra_Datos();

        //Timer para comprobar estado de disopsitivos.
        Timer timer = new Timer();
        TimerTask tarea = new TimerTask() {
            @Override
            public void run() {
                //Tarea periódica para comprobar el estado de los dispositivos de la bd
                Connection connection_timer = null;

                try{
                    //Cogemos el mutex
                    mutex.acquire();
                    try{
                        connection_timer =(Connection) DriverManager.getConnection (url,user,pass);

                        // Leemos de la base de datos
                        Statement s_timer = connection_timer.createStatement();
                        String query_datos = "select * from user";
                        ResultSet r_timer = s_timer.executeQuery(query_datos);
                        
                        while (r_timer.next()) {
                        /*
                            Si el dispositivo está en estado NLOS y comprobamos que ha pasado
                            mas de 10 minutos en ese estado, lo eliminamos de la base de datos
                        */

                            if (r_timer.getString(4).equals("NLOS")){
                                java.sql.Timestamp time_device;
                                //TimeStamp actual
                                Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                                //TimeStamp del dispositivo
                                time_device = r_timer.getTimestamp(6); 
                                //Calculamos la diferencia de minutos
                                if(dif_minutes(time_device,timestamp) > 5){ //Dispositivo fuera de local
                                    
                                    //System.out.println("Dispositivo "+r.getString(3)+ " fuera de alcance durante mas de x minutos");
                                    //Cambiamos estado
                                    String query_datos2 = "update user set state = ? where (address = ? and state = ?) ";
                                    PreparedStatement preparedStmt_datos = connection_timer.prepareStatement(query_datos2);
                                    preparedStmt_datos.setString(1, "OUT");
                                    preparedStmt_datos.setString(2, r_timer.getString(2));
                                    preparedStmt_datos.setString(3, "NLOS");
                                    preparedStmt_datos.executeUpdate();
                                    
                                }else{
                                    
                                    //System.out.println("Dispositivo fuera de alcance");
                                    //System.out.println (r.getInt (1) + " " + r.getString (2)+ " " + r.getString(3) + " "+r.getString(4) + " " +r.getTimestamp(5));
                                    
                                }
                            }else if(r_timer.getString(4).equals("LOS")){
                                
                                //System.out.println("Dispositivo en zona de cobertura");
                                //System.out.println (r.getInt (1) + " " + r.getString (2)+ " " + r.getString(3) + " "+r.getString(4) + " " +r.getTimestamp(5));
                                
                            }     
                    }

                    }catch(SQLException e){
                            System.out.println(e.getMessage());
                    }finally{
                        try{
                            if(connection_timer!=null){
                                connection_timer.close();
                            }
                        }catch(SQLException ex){
                            System.out.println(ex.getMessage());
                        }
                    }
                }catch(InterruptedException e){
                    e.printStackTrace();
                }finally{
                   //Soltamos el mutex
                   mutex.release();
                }
            }
        };//Fin función timer
        //Lanzamos al ejecución periódica de la tarea (cada 5 minutos) 
        timer.schedule(tarea, 60000, 60000);
                
        
        //Conexión para la base de datos
        Connection connection_main = null;
        while(true){ //Bucle infinito de búsqueda
            try{
                connection_main =(Connection) DriverManager.getConnection (url,user,pass);
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

             
                //Limpiamos el vector
                devicesDiscovered.clear();
                //Lanzamos la inquiry
                da.startInquiry(DiscoveryAgent.GIAC, listener);

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
                   Para ello realizamos una query en busca de su dirección MAC
                   */
                   Statement s= connection_main.createStatement();
                   String mac_user = btDevice.getBluetoothAddress();
                   String query_main = "select * from user HAVING address = ";
                   mac_user = "'"+mac_user+"'";
                   query_main= query_main.concat(mac_user);
                   ResultSet r_main = null;

                   try{

                        try{
                            //Cogemos el mutex
                            mutex.acquire();

                            //Realizamos la query a la bd
                            r_main = s.executeQuery(query_main);
                            /*
                                En el caso de que no nos devuelva nada o el dispositivo
                                descubierto ya haya salido de la zona durante el tiempo
                                establecido como (fuera de rango), introducimos el 
                                dispositivo encontrado en la base de datos

                            */
                            if(!r_main.next()) {
                                //Si no está duplicado, escribimos en la base de datos
                                String query1 = " insert into user (address, name_dev,state, discovered_at)"+ " values (?,?,?,?)";
                                PreparedStatement preparedStmt;
                                preparedStmt = connection_main.prepareStatement(query1);

                                //Dirección MAC dispositivo descubierto
                                preparedStmt.setString (1, btDevice.getBluetoothAddress());
                                //Nombre dispositivo descubierto
                                try {
                                    preparedStmt.setString (2, btDevice.getFriendlyName(false));
                                } catch (IOException ex) {
                                    Logger.getLogger(MyDiscoveryListener.class.getName()).log(Level.SEVERE, null, ex);
                                    preparedStmt.setString (2, "Uknown");
                                }
                                //Estado - LOS (Line Of Sight)
                                preparedStmt.setString(3,"LOS");

                                //TimeStamp del dispositivo descubierto
                                preparedStmt.setTimestamp(4, (Timestamp) getCurrentTimestamp());
                                //Ejecutamos la query para añadir al dispositivo en la bd
                                preparedStmt.execute();

                           }else{ //Hay elementos en la base de datos con la misma dirección
                            /*
                                Comprobamos si está en el estado NLOS para volver
                                a actualizarlo a LOS ya que lo acabamos de descubrir,
                                también actualizamos el timestamp  
                                
                            */  if(r_main.getString(4).equals("NLOS")){
                                    //Cambio NLOS -> LOS
                                            String query2 = "update user set state = ?, discovered_at= ?, exit_time= ? where (address = ? and state = 'NLOS')";
                                            PreparedStatement preparedStmt2 = connection_main.prepareStatement(query2);
                                            preparedStmt2.setString(1, "LOS");
                                            preparedStmt2.setTimestamp(2, (Timestamp) getCurrentTimestamp());
                                            preparedStmt2.setTimestamp(3,null);
                                            preparedStmt2.setString(4, r_main.getString(2));
                                            preparedStmt2.executeUpdate();
                                            break;
                                }else{
                                    while(r_main.next())
                                        {
                                            if(r_main.getString(4).equals("NLOS")){
                                                //Cambio NLOS -> LOS
                                                String query2 = "update user set state = ?, discovered_at= ?, exit_time= ? where (address = ? and state = 'NLOS')";
                                                PreparedStatement preparedStmt2 = connection_main.prepareStatement(query2);
                                                preparedStmt2.setString(1, "LOS");
                                                preparedStmt2.setTimestamp(2, (Timestamp) getCurrentTimestamp());
                                                preparedStmt2.setTimestamp(3,null);
                                                preparedStmt2.setString(4, r_main.getString(2));
                                                preparedStmt2.executeUpdate();
                                                break;

                                            }
                                        }
                                }
                                    /*
                                        Query para comprobar si hay algún dispositivo en estado LOS y NLOS
                                        para no añadir duplicados a la base de datos
                                    */

                                    Statement s2= connection_main.createStatement();
                                    String query_main2;
                                    query_main2 = query_main.concat(" and (state = 'LOS' or state = 'NLOS')");
                                    ResultSet r2 = null;
                                    r2 = s2.executeQuery(query_main2);
                                    /*
                                       Si el dispositivo lo hemos insertado previamente,
                                       no lo volvemos a insertar (duplicado)
                                    */
                                    if (!r2.next()){ 
                                        String query3 = " insert into user (address, name_dev,state, discovered_at)"+ " values (?,?,?,?)";
                                         PreparedStatement preparedStmt;
                                         preparedStmt = connection_main.prepareStatement(query3);

                                         //Dirección MAC dispositivo descubierto
                                         preparedStmt.setString (1, btDevice.getBluetoothAddress());
                                         //Nombre dispositivo descubierto
                                         try {
                                             preparedStmt.setString (2, btDevice.getFriendlyName(false));
                                         } catch (IOException ex) {
                                             Logger.getLogger(MyDiscoveryListener.class.getName()).log(Level.SEVERE, null, ex);
                                             preparedStmt.setString (2, "Uknown");
                                         }
                                         //Estado - LOS (Line Of Sight)
                                         preparedStmt.setString(3,"LOS");

                                         //TimeStamp del dispositivo descubierto
                                         preparedStmt.setTimestamp(4, (Timestamp) getCurrentTimestamp());
                                         //Ejecutamos la query para añadir al dispositivo en la bd
                                         preparedStmt.execute();


                                 }//En el caso de que ya lo hayamos insertado, no hacemos nada
                            }
                        }catch(InterruptedException e){
                           e.printStackTrace();
                        }finally{
                            //Soltamos mutex
                            mutex.release();
                        }

                   }catch (SQLException ex) {
                       System.out.println(ex.getMessage());
                   }



                    }//end for
                    lastDiscovered = (Vector) devicesDiscovered.clone();
            
            }catch(SQLException ex){
                System.out.println("Error conexión con base de datos, main");
            }finally {
                try {
                    // Cierra la conexion con la base de datos
                    if(connection_main!=null){
                        connection_main.close();
                    }
                } catch (SQLException ex) {
                    System.out.println(ex.getMessage());
                }
            } 
        }
    }
    
    //Función para devolver el timestamp en segundos concreto
    private static Date getCurrentTimestamp() {

                java.util.Date today = new java.util.Date();
                return new java.sql.Timestamp(today.getTime());
    }
    
    private static long dif_minutes(java.sql.Timestamp t1, java.sql.Timestamp t2){
        long milis_t1 = t1.getTime();
        long milis_t2 = t2.getTime();
        long diff = (milis_t2 - milis_t1)/(1000*60);
                
        return (diff);
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
 
