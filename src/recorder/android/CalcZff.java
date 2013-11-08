
package recorder.android;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Random;

import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.Bundle;
import android.os.Environment;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class CalcZff extends Activity {

	float[] fir, conv, zff, conv2, a, slope;
	int nSamples;
	float length = 12800.0f;
	int noOfSegments;
	int[] segments, sizeSegments;
	int[] onezero;
	short[] audioShorts, finalShorts;
	byte[] data;
	final float slopeThreshold = (float) 0.027;
	String mFileName;
	int size;
	int streamId;
	Button originalButton, newButton, fastButton, slowButton;

	SoundPool soundPool;

	private String outFilePath = null;

	ProgressDialog progress;

	// private MediaPlayer   mPlayer = null;

	boolean playingOriginal = true, playingNew = true, playingFast = true, playingSlow = true;

	/* Calculates slope of zff signal at each point */
	private void calcSlope() {
		slope = new float[size];
		for(int i=1; i<size-1; i++) {
			slope[i] = Math.abs(zff[i+1] - zff[i]);
		}
	}

	private void find_segments() {
		noOfSegments = 0;
		segments = new int[100000];
		sizeSegments = new int[100000];
		nSamples = 0;
		for(int i=0; i<size;) {
			float countZero = 0;
			float chunkSize = length;
			if(chunkSize > size-i)
				chunkSize = size-i;
			for(int j=i; j<i+length && j<size; j++) {
				if(onezero[j] == 0) {
					countZero = (float) (countZero + 1.0);
				}
			}
			Log.d("Count Zero-----------", Float.toString(countZero));
			if(countZero >= 0.6*chunkSize) {
				i += length/2;
			}
			else {
				/*		for(int j=i; j<i+length && j<size; j++) {
					audioShorts[nSamples++] = Short.reverseBytes((short)(a[j]*0x8000));
				}*/
				if(noOfSegments > 0 && i == segments[noOfSegments-1]+length) {
					sizeSegments[noOfSegments-1] += chunkSize;
				}
				else {
					segments[noOfSegments] = i;
					sizeSegments[noOfSegments] = (int) (chunkSize);
					noOfSegments++;
				}
				/*if(noOfSegments 1== 5) {
					return;
				}*/
				i+=length;
			}
		}
		int flag[] = new int[noOfSegments];
		Random r = new Random();
		nSamples = 0;
		int randomSegments = 0;
		for(int i=0; i<noOfSegments/3; ) {
			int index = r.nextInt(noOfSegments);
			if(flag[index] == 1) {
				continue;
			}
			randomSegments++;
			for(int j=segments[index]; j<segments[index]+sizeSegments[index]; j++) {
				audioShorts[nSamples++] = Short.reverseBytes((short)(a[j]*0x8000));
			}
			for(int j=0; j<500; j++) {
				audioShorts[nSamples++] = 0;
			}
			flag[index] = 1;
			i++;
		}

		Log.d("No samples ----------------", Integer.toString(nSamples));
		Log.d("No segments----------------", Integer.toString(noOfSegments));
		Log.d("RandomSegments---------------", Integer.toString(randomSegments));
	}

	/* writes silence-removed signal to wav file */
	private void writeToFile(String filePath) {
		short nChannels = 1;
		int sRate = 16000;
		// int nSamples2 = 0;
		short bSamples = 16;
		audioShorts = new short[size];
		onezero = new int[size];
		for(int i=0; i<size-1; i++) {
			//audioShorts[i] = Short.reverseBytes((short)(a[i]*0x8000));
			//nSamples++;
			if(slope[i] >= slopeThreshold) { // Voice region -- Should be written to output
				//	audioShorts[nSamples2] = Short.reverseBytes((short)(a[i]*0x8000));
				//	audioShorts[nSamples2+1] = Short.reverseBytes((short)(a[i+1]*0x8000));
				//	nSamples2 += 2;
				onezero[i] = 1;
				onezero[i+1] = 1;
				i++;
			}
			else
				onezero[i] = 0;
		}
		find_segments();
		zff = a = slope = null;
		onezero = null;
		System.gc();

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
			randomAccessWriter.writeInt(Integer.reverseBytes(36+data.length)); // File length 
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
			randomAccessWriter.writeInt(Integer.reverseBytes(data.length)); // No. of samples
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

		conv2 = conv = fir = null;
		System.gc();

		outFilePath = Environment.getExternalStorageDirectory().getAbsolutePath();
		outFilePath += "/outAudioFile.wav";
		calcSlope();
		writeToFile(outFilePath);

		setContentView(R.layout.chart);
		originalButton = (Button) findViewById(R.id.OriginalButton);
		newButton = (Button) findViewById(R.id.NewButton);
		fastButton = (Button) findViewById(R.id.playFast);
		slowButton = (Button) findViewById(R.id.playSlow);
		fastButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				onPlay(outFilePath, playingFast, 1.5f);
				if(playingFast) {
					newButton.setEnabled(false);
					slowButton.setEnabled(false);
					originalButton.setEnabled(false);
					fastButton.setText("Stop Playing");
				} else {
					originalButton.setEnabled(true);
					newButton.setEnabled(true);
					slowButton.setEnabled(true);
					fastButton.setText("Play Fast");
				}
				playingFast = !playingFast;
			}

		});
		slowButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				onPlay(outFilePath, playingSlow, 0.7f);
				if(playingSlow) {
					newButton.setEnabled(false);
					fastButton.setEnabled(false);
					originalButton.setEnabled(false);
					slowButton.setText("Stop Playing");
				} else {
					originalButton.setEnabled(true);
					newButton.setEnabled(true);
					fastButton.setEnabled(true);
					slowButton.setText("Play Slow");
				}
				playingSlow = !playingSlow;
			}

		});
		originalButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				onPlay(mFileName, playingOriginal, 1.0f);
				if(playingOriginal) {
					newButton.setEnabled(false);
					fastButton.setEnabled(false);
					slowButton.setEnabled(false);
					originalButton.setText("Stop Playing");
				}
				else {
					newButton.setEnabled(true);
					fastButton.setEnabled(true);
					slowButton.setEnabled(true);
					originalButton.setText("Play Original");
				}
				playingOriginal = !playingOriginal;
			}
		});
		newButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				onPlay(outFilePath, playingNew, 1.0f);
				if(playingNew) {
					fastButton.setEnabled(false);
					slowButton.setEnabled(false);
					originalButton.setEnabled(false);
					newButton.setText("Stop Playing");
				} else {
					originalButton.setEnabled(true);
					fastButton.setEnabled(true);
					slowButton.setEnabled(true);
					newButton.setText("Play Modified");
				}
				playingNew = !playingNew;
			}

		});
	}

	/* Called on clicking Start/Stop button*/
	private void onPlay(String fileName, boolean start, float playbackRate) {
		if (start) {
			startPlaying(fileName, playbackRate);
		} else {
			stopPlaying(fileName);
		}
	}

	/* Starts playing given wav file*/
	@SuppressLint("NewApi")
	private void startPlaying(String fileName, float playbackRate) {
		final float pb = playbackRate;
		soundPool = new SoundPool(4, AudioManager.STREAM_MUSIC, 100);
		final int soundId = soundPool.load(fileName, 1);
		AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		final float volume = mgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		soundPool.setOnLoadCompleteListener(new OnLoadCompleteListener() {
			@Override
			public void onLoadComplete(SoundPool soundPool, int sampleId,
					int status) {
				streamId = soundPool.play(soundId, volume, volume, 1, 0, pb);
			}
		});
		/*mPlayer = new MediaPlayer();
		try {
			mPlayer.setDataSource(fileName);
			mPlayer.prepare();
			mPlayer.start();
		} catch (IOException e) {
			Log.e("Playing.........", "prepare() failed");
		}*/
	}

	/*Stops playing*/
	private void stopPlaying(String fileName) {
		soundPool.stop(streamId);
		/*mPlayer.release();
		mPlayer = null;*/
	}

	/* Builds FIR filter */
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

	/* Applies the filter to signal */
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
