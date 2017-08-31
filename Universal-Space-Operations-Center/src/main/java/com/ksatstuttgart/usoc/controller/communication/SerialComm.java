/*
 * The MIT License
 *
 * Copyright 2017 KSat e.V.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.ksatstuttgart.usoc.controller.communication;

import com.ksatstuttgart.usoc.data.DataSource;
import com.ksatstuttgart.usoc.data.SerialEvent;
import java.awt.event.ActionEvent;
import static java.lang.Thread.sleep;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;

/**
 * <h1>SerialComm</h1>
 * This class provides methods to communicate via a specified serial port.
 * <p>
 *
 *
 * @author Valentin Starlinger
 * @version 1.0
 */
public class SerialComm {

    private SerialPort serialPort;

    private ArrayList<SerialListener> listener = new ArrayList<>();
    private static final String[] BAUDRATES = {"38400", "57600"};
    private static final String[] COMMANDS = {"Ping", "Testing", "Camtest", "Cameras", "Abort", "Prelaunch", "Active", "Reset", "LaunchMacro"};

    private static SerialComm instance;

    public static SerialComm getInstance() {
        if (instance == null) {
            instance = new SerialComm();
        }
        return instance;
    }

    public void start(String sp, int baudrate) {
        if (!sp.isEmpty()) {
            if (serialPort != null && serialPort.isOpened()) {
                close();
            }
            serialPort = new SerialPort(sp);//"/dev/tty.usbserial-A702WKDS");
            try {
                serialPort.openPort();//Open serial port //serialPort.setParams(9600, 8, 1, 0); 
                serialPort.setParams(baudrate, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                int mask = SerialPort.MASK_RXCHAR + SerialPort.MASK_CTS + SerialPort.MASK_DSR;//Prepare mask
                serialPort.setEventsMask(mask);//Set mask
                serialPort.addEventListener(new SerialPortReader());//Add SerialPortEventListener

            } catch (SerialPortException e) {
                //System.out.println(e);
                error("Error: Couldn't open Serialport.\n");
            }
        }
    }

    public void close() {
        try {   
            serialPort.closePort();
        } catch (SerialPortException ex) {
            error("Error: Couldn't close Serialport.");
        }
    }

    public void send(String msg) {
        if(!isOpen()){
            error("Error: Serial port is not connected!");
            return;
        }
        try {
            serialPort.writeBytes(msg.getBytes(StandardCharsets.US_ASCII));
        } catch (SerialPortException ex) {
            error("Error: Couldn't send message: " + msg + ".");
        }
    }

    public static void sendAction(ActionEvent e) {
        System.out.println(SerialCommand.valueOf(e.getActionCommand()).getCommand()+"0\n");
        if (SerialComm.getInstance().isOpen()) {
            String command = SerialCommand.valueOf(e.getActionCommand()).getCommand()+"0\n";
            SerialComm.getInstance().send(command);
        }
    }

    public ArrayList<String> getPorts() {
        ArrayList<String> al = new ArrayList<>();
        al.addAll(Arrays.asList(jssc.SerialPortList.getPortNames()));
        return al;
    }

    public boolean isOpen() {
        return serialPort != null && serialPort.isOpened();
    }

    public List<String> getAvailableCommands() {
        ArrayList<String> commands = new ArrayList<>();
        for (SerialCommand command : SerialCommand.values()) {
            commands.add(command.toString());
        }
        return commands;
    }

    public List<String> getAvailableBaudrates() {
        return Arrays.asList(SerialComm.BAUDRATES);
    }

    /**
     * This class reads chars from the serial port and reports new incoming
     * data. Incoming data is detected by newLine characters.
     */
    private class SerialPortReader implements SerialPortEventListener {

        String buffer = "";

        @Override
        public void serialEvent(SerialPortEvent event) {
            if (event.isRXCHAR()) {//If data is available
                try {
                    for (byte c : serialPort.readBytes(event.getEventValue())) {
                        //every time a newLine char is read, send the previous chars 
                        //to all listeners
                        if (c == '\n') {
                            messageReceived(new SerialEvent(buffer, event.getPortName(),
                                    System.currentTimeMillis(), DataSource.SERIAL));
                            System.out.println(buffer);
                            buffer = "";
                        } else {
                            buffer += (char)c;
                        }
                    }
                } catch (SerialPortException ex) {
                    error("Error: Couldn't read incoming Bytes.");
                }
            }
        }
    }

    private void messageReceived(SerialEvent e) {
        for (SerialListener l : listener) {
            l.messageReceived(e);
        }
    }

    private void error(String msg) {
        for (SerialListener l : listener) {
            l.error(msg);
        }
    }

    public void addSerialListener(SerialListener l) {
        listener.add(l);
    }

    public void removeSerialListener(SerialListener l) {
        listener.remove(l);
    }

}
