
package recorder.android;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.app.ProgressDialog;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class CalcZff extends Activity {

	float[] fir, conv, zff, conv2, a, slope;
	short[] audioShorts, finalShorts;
	byte[] data;
	String mFileName;
	int size;
	Button originalButton, newButton;

	private String outFilePath = null;

	ProgressDialog progress;

	private MediaPlayer   mPlayer = null;

	boolean playingOriginal = true, playingNew = true;


	private void calcSlope() {
		slope = new float[size];
		for(int i=1; i<size-1; i++) {
			slope[i] = Math.abs(zff[i+1] - zff[i]);
		}
	}

	private void writeToFile(String filePath) {
		short nChannels = 1;
		int sRate = 16000;
		short bSamples = 16;
		audioShorts = new short[size];
		int nSamples = 0;
		for(int i=0; i<size-1; i++) {
			//audioShorts[i] = Short.reverseBytes((short)(zff[i]*0x8000));
			if(slope[i] >= 0.02) {
				audioShorts[nSamples] = Short.reverseBytes((short)(a[i]*0x8000));
				audioShorts[nSamples+1] = Short.reverseBytes((short)(a[i+1]*0x8000));
				nSamples += 2;
				i++;
			}
			/*else
				audioShorts[i] = 0;*/
		}
		finalShorts = new short[nSamples];
		for(int i=0; i<nSamples; i++){
			finalShorts[i] = audioShorts[i];
		}
		data = new byte[finalShorts.length*2];
		ByteBuffer buffer = ByteBuffer.wrap(data);
		ShortBuffer sbuf = 	buffer.asShortBuffer();
		sbuf.put(finalShorts);
		data = buffer.array();
		Log.d("Data length------------------------------", Integer.toString(data.length));
		RandomAccessFile randomAccessWriter;
		try {
			randomAccessWriter = new RandomAccessFile(filePath, "rw");
			randomAccessWriter.setLength(0); // Set file length to 0, to prevent unexpected behaviour in case the file already existed
			randomAccessWriter.writeBytes("RIFF");
			randomAccessWriter.writeInt(Integer.reverseBytes(36+data.length)); // Final file size not known yet, write 0 
			randomAccessWriter.writeBytes("WAVE");
			randomAccessWriter.writeBytes("fmt ");
			randomAccessWriter.writeInt(Integer.reverseBytes(16)); // Sub-chunk size, 16 for PCM
			randomAccessWriter.writeShort(Short.reverseBytes((short) 1)); // AudioFormat, 1 for PCM
			randomAccessWriter.writeShort(Short.reverseBytes(nChannels));// Number of channels, 1 for mono, 2 for stereo
			randomAccessWriter.writeInt(Integer.reverseBytes(sRate)); // Sample rate
			randomAccessWriter.writeInt(Integer.reverseBytes(sRate*bSamples*nChannels/8)); // Byte rate, SampleRate*NumberOfChannels*BitsPerSample/8
			randomAccessWriter.writeShort(Short.reverseBytes((short)(nChannels*bSamples/8))); // Block align, NumberOfChannels*BitsPerSample/8
			randomAccessWriter.writeShort(Short.reverseBytes(bSamples)); // Bits per sample
			randomAccessWriter.writeBytes("data");
			randomAccessWriter.writeInt(Integer.reverseBytes(data.length)); // Data chunk size not known yet, write 0
			randomAccessWriter.write(data);
			randomAccessWriter.close();

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		byte[] buff = new byte[1024];


		mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
		mFileName += "/audiofile.wav";
		Log.d("Audio filename-------", mFileName);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			@SuppressWarnings("resource")
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(mFileName));
			int read;
			while ((read = in.read(buff)) > 0)
			{
				out.write(buff, 0, read);
			}
			out.flush();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		byte[] audioBytes = out.toByteArray();
		a = new float[1000000];
		size = 0;
		for(int i=44, j=0; (i+1)<audioBytes.length; i+=2, j++)
		{
			a[j] = (audioBytes[i+0] & 0xFF) | ((audioBytes[i+1] << 8));
			a[j] *= 3.0517578125e-5f;
			size++;
		}	


		conv2 = new float[1000000];

		int n =10; 
		int dim = build_zff(n);

		zff_mat(dim,size);


		outFilePath = Environment.getExternalStorageDirectory().getAbsolutePath();
		outFilePath += "/outAudioFile.wav";
		calcSlope();
		writeToFile(outFilePath);

		setContentView(R.layout.chart);
		originalButton = (Button) findViewById(R.id.OriginalButton);
		newButton = (Button) findViewById(R.id.NewButton);
		originalButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				onPlay(mFileName, playingOriginal);
				if(playingOriginal) {
					originalButton.setText("Stop Playing Original");
				}
				else {
					originalButton.setText("Start Playing Original");
				}
				playingOriginal = !playingOriginal;
			}
		});
		newButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				onPlay(outFilePath, playingNew);
				if(playingNew) {
					newButton.setText("Stop Playing New");
				} else {
					newButton.setText("Start Playing New");
				}
				playingNew = !playingNew;
			}

		});
	}

	private void onPlay(String fileName, boolean start) {
		if (start) {
			startPlaying(fileName);
		} else {
			stopPlaying(fileName);
		}
	}

	private void startPlaying(String fileName) {
		mPlayer = new MediaPlayer();
		try {
			mPlayer.setDataSource(fileName);
			mPlayer.prepare();
			mPlayer.start();
		} catch (IOException e) {
			Log.e("Playing.........", "prepare() failed");
		}
	}

	private void stopPlaying(String fileName) {
		mPlayer.release();
		mPlayer = null;
	}

	int build_zff(int n) {
		int win = 2*n;
		fir = new float[10*n];
		for(int i=0; i<n; i++) {
			fir[i] = ((i+1) * (i+2))/2;
		}


		int count = n;

		for(int j=n-2; j>=0; j--) {
			fir[count++] = fir[j];
		}

		for(int i=0; i<count; i++) {
			fir[i] = fir[i]/win;
		}


		int i1;
		float tmp, max = (float) 0.0;

		for(int i=count; i<count*2-1; i++) {
			fir[i] = (float) 0.0;
		}

		conv = new float[10*n];

		for(int i=0; i<count*2-1; i++) {
			i1 = i;
			tmp = (float) 0.0;
			for(int j=0; j<count; j++) {
				if(i1>=0 && i1<count) {
					tmp = tmp + (fir[i1]*fir[j]);
				}
				i1--;
			}
			conv[i] = tmp;
			if(conv[i]>max) {
				max = conv[i];
			}
		}

		for(int i=0; i<count*2-1; i++){
			fir[i] = conv[i]/max;
		}
		return count*2-1;
	}

	void zff_mat(int dim, int n) {
		int w = (dim + 1)/2;
		int i1;
		float tmp;
		// Change value here
		for(int i=0; i<n+dim-1; i++) {
			i1 = i;
			tmp = (float) 0.0;
			for(int j=0; j<dim; j++) {
				if(i1>=0 && i1<n) {
					tmp += a[i1] * fir[j];
				}
				i1--;
				conv2[i] = tmp;
			}
		}
		zff = new float[10*n];
		for(int i=w-1, j=0; i<n+w-1; i++, j++) {
			zff[j] = conv2[i];
		}
	}

}
