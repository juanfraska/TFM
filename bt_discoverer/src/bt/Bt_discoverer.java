/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bt;
//package bt;
 
import static bt.Bt_discoverer.devicesDiscovered;
import java.io.IOException;
import java.util.Vector;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.intel.bluetooth.BlueCoveLocalDeviceProperties;
import javax.bluetooth.LocalDevice;
//API Bluecove
import javax.bluetooth.*;


public class Bt_discoverer {

    public static final Vector devicesDiscovered = new Vector(); //Vector de dispositivos descubiertos
    public static void main(String[] args) throws BluetoothStateException, InterruptedException, IOException {
        
        final Object completedEvent = new Object();
        devicesDiscovered.clear();
        // Dispositivo local
         LocalDevice myBT = LocalDevice.getLocalDevice();
        System.out.println("Local properties | " + " Name: " + myBT.getFriendlyName() + " Addr: "+ myBT.getBluetoothAddress());
        DiscoveryAgent  da= myBT.getDiscoveryAgent();
        //Discovery listener
        DiscoveryListener listener = new MyDiscoveryListener(completedEvent) ;
        //Lanzamos el inquiry search constantemente
        while(true){
            da.startInquiry(DiscoveryAgent.GIAC, listener);
            //Esperamos a que termine la búsqueda
            synchronized (completedEvent)
            {
                completedEvent.wait();
            }

        }
    }
    
}

class MyDiscoveryListener implements javax.bluetooth.DiscoveryListener{
            Object completedEvent;
            public MyDiscoveryListener(Object event){
                    completedEvent = event;
            
            }

            public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {  //Device class : clase de dispositivo
                //Añadimos dispositivo a vector
                if(!devicesDiscovered.contains(btDevice)){
                    devicesDiscovered.addElement(btDevice);
                }
                
                //Datos del dispositivo encontrado
                System.out.println("Direccion Bluetooth " + btDevice.getBluetoothAddress());
                int class_device = cod.getMajorDeviceClass();
                if(class_device == 512){
                    System.out.println("Teléfono móvil");

                }
                try {
                    System.out.println("Nombre de dispositivo "+btDevice.getFriendlyName(false));
                } catch (IOException ex) {
                    Logger.getLogger(MyDiscoveryListener.class.getName()).log(Level.SEVERE, null, ex);
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