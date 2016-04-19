package com.example.attracti.audiorecorderstreamingclient;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity {


    private static final String TAG = MainActivity.class.getSimpleName();

    private WebSocketClient mWebSocketClient;
    private String ADDRESS = "192.168.1.143";
    private String PORT = "8080";
    private String URI_STRING = "ws://" + ADDRESS + ":" + PORT;

    static byte[] mockBytes;
    static byte[] receivedbytes = null;
    byte[] allbytes;


    private String mInputFileName = Environment.getExternalStorageDirectory().getAbsolutePath() + "/servertest.wav";
    private String mOutputFileName = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.wav";

    //----------------Audio config--------------------------

    private static final int AUDIO_RATE = 11025;
  //  private static final int AUDIO_RATE = 8000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_STEREO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private final int DELAY = 5;
    private final int BUFFER_SCALE = 2;
    private final int TRACK_BUFFER_SIZE = AudioTrack.getMinBufferSize(AUDIO_RATE, CHANNEL, FORMAT);
    private int RECORDER_BUFFER_SIZE = AudioRecord.getMinBufferSize(AUDIO_RATE, CHANNEL, FORMAT)*BUFFER_SCALE;

    //audio track for play received data from server
    AudioTrack track = new AudioTrack(
            AudioManager.STREAM_MUSIC,
            AUDIO_RATE, CHANNEL,
            AudioFormat.ENCODING_PCM_16BIT,
            TRACK_BUFFER_SIZE,
            AudioTrack.MODE_STREAM);

    // the audio recorder
    private AudioRecord recorder;
    private boolean RECORD_STATUS = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initButtonListener();
        connectWebSocket();
    }

    private void initButtonListener() {
        Button play = (Button) findViewById(R.id.play);
        play.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startStreaming();
            }
        });
    }

    /**
     * Method for playing an audio
     */
    public void playAudio() {
        track.play();
        track.write(allbytes, 0, allbytes.length);
    }

    /**
     * Set connection to the WebSocket
     */
    private void connectWebSocket() {
        URI uri;
        try {
            uri = new URI(URI_STRING);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        mWebSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                Log.i("Websocket", "Opened");
//                for (int i = 0; i < 5; i++) {
//                    mWebSocketClient.send(Arrays.copyOfRange(mockBytes, mockBytes.length / 4 * i, mockBytes.length / 4 * (i + 1)));
//                }
                //   mWebSocketClient.send(mockBytes);
            }

            @Override
            public void onMessage(String s) {
            }

            @Override
            public void onMessage(final ByteBuffer bytes) {

                //save received bytes from server to local variable
                allbytes = bytes.array();

                //start play received bytes
                playAudio();
//                try {
//                    allbytes = concat(receivedbytes, mockBytes.array());
//                    saveBytesFromServer(allbytes);
//                    receivedbytes = allbytes;
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                Log.i("Websocket", "Closed " + s);
            }

            @Override
            public void onError(Exception e) {
                Log.i("Websocket", "Error " + e.getMessage());
            }
        }

        ;
        mWebSocketClient.connect();
    }

    /**
     * Method for streaming voice record
     * in realtime by webSocket
     */
    public void startStreaming() {

        Thread streamThread = new Thread(new Runnable() {

            @Override
            public void run() {

                byte[] buffer = new byte[RECORDER_BUFFER_SIZE];

                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, AUDIO_RATE, CHANNEL, FORMAT, RECORDER_BUFFER_SIZE * DELAY);

                //start  record voice
                recorder.startRecording();


                while (RECORD_STATUS == true) {
                    Log.e(TAG, "Send data");

                    //reading data from MIC into buffer
                    RECORDER_BUFFER_SIZE = recorder.read(buffer, 0, buffer.length);



                    //send data to server
                    mWebSocketClient.send(buffer);

                    Log.d("MinBufferSize: ", RECORDER_BUFFER_SIZE + "");
                    Log.d("Bytes.length: ", buffer.length + "");

                }
            }
        });
        streamThread.start();
    }

    //convert an existing audio file ito mockBytes
    private void initMockBytes() {
        int bytesRead;

        try {
            FileInputStream fis = new FileInputStream(new File(mOutputFileName));

            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            byte[] b = new byte[1024];

            while ((bytesRead = fis.read(b)) != -1) {
                bos.write(b, 0, bytesRead);
            }

            mockBytes = bos.toByteArray();

            Log.i("Bytes", String.valueOf(mockBytes.length));

        } catch (Exception e) {
            Toast.makeText(this, "Error starting draw. ", Toast.LENGTH_SHORT).show();
            Log.i("Error", "print stackTrace");
            e.printStackTrace();
        }
    }


    /**
     * Method for save mockBytes(voice_record) from server into the file
     *
     * @param array
     * @throws IOException
     */
    public void saveBytesFromServer(byte[] array) throws IOException {
        FileOutputStream fileName = new FileOutputStream(mInputFileName);

        //create output stream for file
        BufferedOutputStream stream = new BufferedOutputStream(fileName);
        //write data in file
        stream.write(array, 0, array.length);

        stream.flush();
        stream.close();
    }

    /**
     * Method for the concat already received data
     * with new data from the server
     *
     * @param first  already received data
     * @param second new data
     * @return all mockBytes from first and second  arrays
     */

    public static byte[] concat(byte[] first, byte[] second) {
        if (first == null) {
            return second;
        } else {
            byte[] result = Arrays.copyOf(first, first.length + second.length);
            System.arraycopy(second, 0, result, first.length, second.length);
            return result;
        }
    }
}
