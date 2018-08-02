package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 * Citation: https://courses.engr.illinois.edu/cs425/fa2013/L7.fa13.ppt
 *
 */
public class GroupMessengerActivity extends Activity {

    private static final String TAG = "GroupMessengerActivity";
    static final int SERVER_PORT = 10000;

    //Port numbers for all the AVDs
    static final int[] ports = {11108, 11112, 11116, 11120, 11124};

    //A list of port numbers and their failure status: 'W' for Working and 'F' for failed
    ConcurrentHashMap<Integer, String> portMap = new ConcurrentHashMap<Integer, String>(10);

    //Current AVDs' port number
    private int myPort;

    //a variable that keeps the sequence of all the messages in content provider
    private int cvSequenceNumber = 0;

    //a counter that keeps track of message IDs
    private int msg_counter = 1;

    //Citation: https://docs.oracle.com/javase/7/docs/api/java/util/PriorityQueue.html
    //A priority queue which stores the messages that needs to be delivered
    private PriorityQueue<HoldBackMessage> holdBackQueue;

    //Citation: https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/atomic/AtomicInteger.html
    //A synchronized variable that holds the largest sequence number until now
    private AtomicInteger largestSeqNo;

    //Failure timeout
    //private static final int timeout = 1000;

    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        //Initializing the port map with list of avds in the network: status: W for Working and F for fail
        portMap.put(11108,"W");
        portMap.put(11112,"W");
        portMap.put(11116,"W");
        portMap.put(11120,"W");
        portMap.put(11124,"W");

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = Integer.parseInt(portStr) * 2;

        //Initialize the largest sequence number to 0
        largestSeqNo = new AtomicInteger(0);

        //Initialize the priority queue that assigns priority to smaller sequence numbers
        holdBackQueue = new PriorityQueue<HoldBackMessage>(100, new Comparator<HoldBackMessage>() {
            @Override
            public int compare(HoldBackMessage lhs, HoldBackMessage rhs) {
                return Double.compare(lhs.getSeqNo(),rhs.getSeqNo());
            }
        });

        try{
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        }
        catch(IOException e){
            Log.e(TAG, "Can't create a ServerSocket");
            Log.e(TAG,e.toString());
            return;
        }

        //On clicking Send button, send the message written in the textbox to all the AVDs
        final Button sendButton = (Button) findViewById(R.id.button4);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText editText = (EditText) findViewById(R.id.editText1);
                if(editText.getText().toString() != null){
                    String msg = editText.getText().toString() + "\n";
                    //Log.d(TAG, "Msg written: "+msg);
                    editText.setText("");

                    //Send message with identifier(M_port number) and the message string after @
                    String msgWithId = "M"+msg_counter+"_"+myPort+"@"+msg;
                    msg_counter++; //Increment the message counter

                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgWithId);
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            //An infinite loop to ensure that server is always listening
            //Citation: https://docs.oracle.com/javase/tutorial/networking/sockets/clientServer.html
            while(true) {
                try {
                    Socket socket = serverSocket.accept(); //accepts the connection whenever there is a client request

                    //To read the data from the InputStream connected to the socket
                    InputStreamReader clientInput = new InputStreamReader(socket.getInputStream());
                    BufferedReader in = new BufferedReader(clientInput);
                    String str = in.readLine();
                    Log.d(TAG,"Message read on server side: "+str);

                    if(str != null){
                        if(str.startsWith("A")){ //if it is an agreement

                            //Split the received string into msgID, msgString and agreed Sequence number
                            String[] components = str.split("@");
                            String msgId = components[1];
                            String msgString = components[2];
                            double agreedSeqNo = Math.ceil(Double.parseDouble(components[3])*100)/100;

                            //Create a new Message object with the given sequence number and deliverable as true
                            //Remove the existing message in the queue and add the updated one
                            HoldBackMessage hbm = new HoldBackMessage(msgId,msgString,agreedSeqNo,true);
                            holdBackQueue.remove(hbm);
                            hbm.setSeqNo(agreedSeqNo);
                            hbm.setDeliverable(true);
                            holdBackQueue.add(hbm);

                            //Set the largest sequence number to the max of largestSeqNo and agreedSeqNo
                            largestSeqNo.set(Math.max((int)agreedSeqNo,largestSeqNo.get()));
                            //Log.d(TAG,"Largest sequence Number after agreement: "+largestSeqNo.get());

                            //If the priority queue has messages that can be delivered, deliver the message
                            while(!holdBackQueue.isEmpty()){
                               // Log.d("Is peek deliverable? ", holdBackQueue.peek().getMsgId() + " : "+holdBackQueue.peek().isDeliverable());
                                if(holdBackQueue.peek().isDeliverable()) {
                                    HoldBackMessage msg = holdBackQueue.poll();
                                   // Log.d(TAG, "The msg removed from priority queue: " + msg.getMsgId() + " Seq no:" + msg.getSeqNo());

                                    publishProgress(String.valueOf(msg.getSeqNo()), msg.getMsgId(), msg.getMsgString());
                                }
                                else
                                    break;
                            }
                        }
                        else if(str.startsWith("M")){ //if it is a message
                            String msgId = str.substring(0,str.indexOf("@"));
                            String msgString = str.substring(str.indexOf("@")+1, str.length());
                            Log.d(TAG,msgId+" "+msgString);

                            int port1 = myPort/2 - 5500;
                            double proposedSeqNo = (double)largestSeqNo.get() + 1 + port1/100.0;

                            largestSeqNo.set(Math.max((int)proposedSeqNo,largestSeqNo.get()));

                            //Log.d(TAG,"Proposed value for msg: " +msgId+": "+ String.valueOf(proposedSeqNo));

                            holdBackQueue.add(new HoldBackMessage(msgId,msgString,proposedSeqNo,false));

                            //Log.d(TAG,"Largest seq number now after proposal: "+largestSeqNo.get());
                            PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);
                            printWriter.println(String.valueOf(proposedSeqNo));
                        }
                        else if(str.startsWith("N")){ //if it is a failure notification message

                            String port = str.substring(str.indexOf("@")+1,str.length());

                            //Change the status of that avd to Failed
                            portMap.replace(Integer.parseInt(port), "F");

                            //Iterate through the hold back queue to delete all the messages from failed queue
                            Iterator<HoldBackMessage> itr = holdBackQueue.iterator();
                            while(itr.hasNext()){
                                HoldBackMessage temp = itr.next();
                                String msgId = temp.getMsgId();

                                if(msgId.contains(port)){
                                    Log.d("TAG", "Present: "+msgId + " " + temp.getMsgString() +" "+ temp.getSeqNo());
                                    holdBackQueue.remove(temp);
                                }
                            }

                            //If the priority queue has messages that can be delivered, deliver the message
                            while(!holdBackQueue.isEmpty() && holdBackQueue.peek().isDeliverable()){
                                HoldBackMessage msg = holdBackQueue.poll();
                                //Log.d(TAG, "Notification part: The msg removed from priority queue: "+msg.getMsgId()+" Seq no:"+msg.getSeqNo());
                                publishProgress(String.valueOf(msg.getSeqNo()),msg.getMsgId(),msg.getMsgString());
                            }
                        }
                    }
                    //close the socket once the message is read and printed
                   // socket.close();
                } catch (IOException ioe) {
                    Log.e(TAG, "ServerTask socket IOException");
                }
            }
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */

            //Log.d(TAG, "onProgressUpdate "+strings[0].trim());
            String strReceived = strings[2].trim();
            TextView textView = (TextView) findViewById(R.id.textView1);

            //Create a ContentValues object which stores key-value pair with sequence number as key and value as the message
            ContentValues cv = new ContentValues();
            cv.put(KEY_FIELD, Integer.toString(cvSequenceNumber));
            cv.put(VALUE_FIELD, strReceived);

            //built the URI
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
            uriBuilder.scheme("content");
            Uri mUri = uriBuilder.build();

            try {
                getContentResolver().insert(mUri, cv); //insert the key-value pair in Sqlite db
                //Printing content provider Sequence Number, agreed sequence number and Message ID
                textView.append(cvSequenceNumber+" "+strings[0].trim() + " " + strings[1].trim() + "\t\n");
                cvSequenceNumber++; //Increment the sequence number
            } catch (Exception e) {
                Log.e(TAG, e.toString());
                return;
            }

            return;
        }
    }

    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     *
     */
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

            String msgToSend = msgs[0];

            //stores the max of all the proposals from all the avds; currently assigned to the largestSeqNo+1
            double maxProposedSeqNo = 0;

            for (int i = 0; i < ports.length; i++) {
                try {

                    if(portMap.get(ports[i]) == "W"){ //if the port is in working state

                        Socket socket = new Socket();

                        //Read timeout set to 1000ms
                        //socket.setSoTimeout(timeout);
                        socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), ports[i]));

                        //Open a PrintWriter on socket's output stream to write the message
                        //Citation: https://docs.oracle.com/javase/tutorial/networking/sockets/readingWriting.html
                        PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);
                        printWriter.print(msgToSend); //write the message entered by the user to the PrintWriter
                        printWriter.flush();

                        //Read proposal
                        InputStreamReader input = new InputStreamReader(socket.getInputStream());
                        BufferedReader in = new BufferedReader(input);
                        String str = in.readLine();

                        Log.d(TAG, "ClientTask read proposal for " + msgToSend + " from:" + ports[i] + ": " + str);
                        if (str != null) {
                            if (Double.parseDouble(str) > maxProposedSeqNo)
                                maxProposedSeqNo = Double.parseDouble(str);
                        }
                        //in.close();
                    }
                }
                catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                }
                catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException on Message read: "+msgToSend + ": "+ports[i]+" "+e.getMessage());
                    e.printStackTrace();
                    handleFailure(ports[i]);
                }

            }

            //Get the maximum of all the proposals which will be the agreed sequence number, multicast it to all the avds
            Log.d(TAG,msgToSend + " Max proposed seq no CT: "+maxProposedSeqNo);

            double agreedSeqNo = Math.ceil(maxProposedSeqNo * 100)/100;
            Log.d(TAG, "Agreed Seq No:"+String.valueOf(agreedSeqNo));

            String agreement = "A@"+msgToSend.trim()+"@"+String.valueOf(agreedSeqNo);

            for(int i=0;i<ports.length;i++) {
                try {
                    if(portMap.get(ports[i]) == "W"){

                        Socket socket = new Socket();
                        //Socket Timeout set to 1000ms
                        //socket.setSoTimeout(timeout);
                        socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), ports[i]));

                        //Open a PrintWriter on socket's output stream to send agreement
                        PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);
                        printWriter.println(agreement); //write the message entered by the user to the PrintWriter
                        Log.d(TAG, "Agreement message written on port " + ports[i] + " : " + agreement);
                    }
                }
                catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                }
                catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException agreement: "+agreement);
                    handleFailure(ports[i]);
                }
            }
            return null;
        }
    }


    /*
    Function to handle the AVD failure
     */
    public void handleFailure(int port){

        //To all the AVDs that are alive, send the notification message to notify about the avd failure
        for (int i = 0; i < ports.length; i++) {
            try {
                if(portMap.get(ports[i]) == "W" && ports[i] != port){
                    Socket socket = new Socket();
                    //Socket Timeout set to 1000ms
                    //socket.setSoTimeout(timeout);
                    socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), ports[i]));


                    PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);
                    String notification = "N@"+String.valueOf(port);

                    printWriter.println(notification); //write the message entered by the user to the PrintWriter
                    Log.d(TAG, "Notification written on port " + ports[i] + " : " + notification);
                }
            }
            catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            }
            catch (IOException e) {
                Log.e(TAG, "Socket exception on notification: "+port);
            }
        }
    }
}
