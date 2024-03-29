/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chedima.btscaleviewer;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BluetoothViewerService {

    private static final String TAG = BluetoothViewerService.class.getSimpleName();
    private static final boolean D = true;

    private static final int STATE_NONE = 0;       // we're doing nothing
    private static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    public static final int MSG_NOT_CONNECTED = 10;
    public static final int MSG_CONNECTING = 11;
    public static final int MSG_CONNECTED = 12;
    public static final int MSG_CONNECTION_FAILED = 13;
    public static final int MSG_CONNECTION_LOST = 14;
    public static final int MSG_LINE_READ = 21;
    public static final int MSG_BYTES_WRITTEN = 22;
    public static final int REFRESHRATE = 500;

    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;


    /**
     * Prepare a new Bluetooth session.
     *
     * @param handler A Handler to send messages back to the UI Activity
     */
    public BluetoothViewerService(Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    /**
     * Set the current state of the connection
     *
     * @param state An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        try {
            mConnectThread = new ConnectThread(device);
            mConnectThread.start();
            setState(STATE_CONNECTING);
            sendMessage(MSG_CONNECTING, device.getName());
        } catch (SecurityException e) {
            Log.e(TAG, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, e.getMessage(), e);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, e.getMessage(), e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, e.getMessage(), e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (D) Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        setState(STATE_CONNECTED);
        sendMessage(MSG_CONNECTED, device.getName());
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.shutdown();
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_NONE);
        sendMessage(MSG_NOT_CONNECTED);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    private void sendLineRead(String line) {
        mHandler.obtainMessage(MSG_LINE_READ, -1, -1, line).sendToTarget();
    }

    private void sendBytesWritten(byte[] bytes) {
        mHandler.obtainMessage(MSG_BYTES_WRITTEN, -1, -1, bytes).sendToTarget();
    }

    private void sendMessage(int messageId, String deviceName) {
        mHandler.obtainMessage(messageId, -1, -1, deviceName).sendToTarget();
    }

    private void sendMessage(int messageId) {
        mHandler.obtainMessage(messageId).sendToTarget();
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        setState(STATE_NONE);
        sendMessage(MSG_CONNECTION_FAILED);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        setState(STATE_NONE);
        sendMessage(MSG_CONNECTION_LOST);
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
            mmDevice = device;
            BluetoothSocket tmp = null;

            Log.i(TAG, "calling device.createRfcommSocket with channel 1 ...");
            try {
                // call hidden method, see BluetoothDevice source code for more details:
                // https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/bluetooth/BluetoothDevice.java
                Method m = device.getClass().getMethod("createRfcommSocket", new Class[]{ int.class });
                tmp = (BluetoothSocket) m.invoke(device, 1);  // channel = 1
                Log.i(TAG, "setting socket to result of createRfcommSocket");
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
                connectionFailed();
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothViewerService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        //private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            //OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                //tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            //mmOutStream = tmpOut;
        }

        private boolean stop = false;
        private boolean hasReadAnything = false;

        public void shutdown() {
            stop = true;
            if (!hasReadAnything) return;
            if (mmInStream != null) {
                try {
                    mmInStream.close();
                    Log.d(TAG, "InputStream in Connected closed properly.");
                } catch (IOException e) {
                    Log.e(TAG, "close() of InputStream failed.");
                }
            }
        }

        public void run() {

  
         	final DataInputStream in = new DataInputStream(mmInStream);  
        	int b0, b1, b2; b0=b1=b2=0;

        	// ��� �������� ���� ��������� ������, ��� � ������ ������� ������� �������.
        	// � ����� �� ����� ��������� ������� ����. ������� � ������ ������
        	ExecutorService executor = Executors.newFixedThreadPool(2);
        	   Callable<Integer> readTask = new Callable<Integer>() {
        	        @Override
        	        public Integer call() throws Exception {
        	            return ((int)in.readByte())&0xFF;
        	        }
        	    };
        	while (!stop) {
        		try {
        		    Future<Integer> future = executor.submit(readTask);
        			b0=b1;
        			b1=b2;
        			b2= future.get(1000, TimeUnit.MILLISECONDS);
               			
        			if (b0==255 && b2==254 && (b1!=255 && b1!=254))
	        			{
	        				int pleaseSendThisFuckingInteger = (int) b1 & 0xFF;
	        				sendLineRead(Integer.toString(pleaseSendThisFuckingInteger));
	        			}

        		} 
        		catch (InterruptedException e) {
        			connectionLost();
        			break;
        			
        		}
        		catch (ExecutionException e){
        			connectionLost();
        			break;
        			
        		}
        		catch (TimeoutException e){
        			shutdown();
        			cancel();
        			connectionLost();
        			break;
        			
        		}
        	}  	
/*        	DataInputStream in = new DataInputStream(mmInStream);  
        	byte b0, b1, b2; b0=b1=b2=0;
        	long lastReadTime=System.currentTimeMillis();
     
        	while (!stop) {
        		try {
        			b0=b1;
        			b1=b2;
        			b2=in.readByte();
        			
        			if (System.currentTimeMillis()-lastReadTime>2000)
        				throw new IOException();
        			
        			if (b0==-1 && b2==-2 && (b1!=-1 && b1!=-2))
	        			{
	        				int pleaseSendThisFuckingInteger = (int) b1 & 0xFF;
	        				sendLineRead(Integer.toString(pleaseSendThisFuckingInteger));
	        			}

        		} catch (IOException e) {
        			connectionLost();
        			break;
        		}
        	}
  */      

        
        		
   

        }

        /**
         * Write to the connected OutStream.
         *
         * @param bytes The bytes to write
         */
        public void write(byte[] bytes) {
   /*         try {
        //        mmOutStream.write(bytes);
                sendBytesWritten(bytes);
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }*/
        }

        public void cancel() {
            try {
                mmSocket.close();
                Log.d(TAG, "mmSocket in Connected closed properly.");
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
