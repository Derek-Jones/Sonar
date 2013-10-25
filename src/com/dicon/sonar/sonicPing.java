package com.dicon.sonar;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
//import android.os.Process;

public class sonicPing {
	AudioTrack at;
	AudioRecord ar;
	static int sType = AudioManager.STREAM_MUSIC;
	static int sRate = AudioTrack.getNativeOutputSampleRate(sType);
	static int[] possibleRates = {48000, 44100, 22050, 11025, 8000};
	static int chirpLength; //milli seconds
	static int chirpPause; //milli seconds
	static int chirpRepeat;
	static int carrierFreq; //Hz
	static int bandwidth; //Hz
	static int bSize; //Chirp
	static int bResSize; //Result
	static int bsSize; //Chirp sequence
	static int sPeriod; //Chirp sequence period (in shorts)
	static int addRecordLength; //milli seconds
	static int brSize; //Recording used buffer size
	static int brSizeInc; //Recording true buffer sized for higher minBufferSize demands
	short[] chirp;
	short[] chirp_sequence;
	short[] recording;
	float[] result;
	float[] periodBuffer;
	float distFactor = 1.f;
	float[][] lastDistance = new float[5][2]; //0/0 = not set. Otherwise [0]->Distance, [1]->Intensity
	boolean first = true;
	boolean camMic = true;
	public int error = 0;
	public int error_detail = 0;
	
	public sonicPing() {
		this(1, 100, 3000, 2000, 500, 10);
		//Log.d("sonar", "default contructor");
	}
	
	public sonicPing(int msChirpLength, int msChirpPause, int HzCarrierFreq, int HzBandwidth, int msAddRecordLength, int nChirpRepeat) {
		//Log.d("sonar", "sonicPing() (constructor)");
		sRate = getMaxRate();
		//Log.d("sonar", "sRate = " + sRate);
		if (sRate < 1) {
			error = -1;
			return;
		}

		chirpLength = msChirpLength;
		chirpPause = msChirpPause;
		carrierFreq = HzCarrierFreq;
		chirpRepeat = nChirpRepeat;
		bandwidth = HzBandwidth;
		bSize =  sRate * chirpLength / 1000;
		addRecordLength = msAddRecordLength;
		brSize =  sRate * (addRecordLength+chirpRepeat*(chirpLength+chirpPause)) / 1000;
		brSizeInc = Math.max(brSize, AudioRecord.getMinBufferSize(sRate, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT)*16); //Ugly fix for Samsung (Suck it!)
		bResSize =  sRate * (chirpPause-2*chirpLength) / 1000;
		sPeriod = sRate * (chirpLength+chirpPause) / 1000;
		bsSize =  chirpRepeat * sPeriod;
		distFactor = 340/(float)sRate/2.f;
		
		//Log.d("sonar", "brSize = " + brSize + ", brSizeInc = " + brSizeInc + ", minBufferSize = " + AudioRecord.getMinBufferSize(sRate, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT));
		
		chirp = new short[bSize];
		chirp_sequence = new short[bsSize];
		buildChirp(chirp, chirp_sequence);
		
		at = new AudioTrack(sType, sRate, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT, bsSize*2, AudioTrack.MODE_STATIC);
		if (at == null) {
			error = -2;
			return;
		}
		if (at.write(chirp_sequence, 0, bsSize) < bsSize) {
			error = -3;
			return;
		}
		
		recording = new short[brSizeInc];
		
		result = new float[bResSize];
		periodBuffer = new float[sPeriod];
		
		for (int i = 0; i < 5; i++) {
			lastDistance[i][0] = 0.f;
			lastDistance[i][1] = 0.f;
		}
	}

	private boolean checkRate(int rate) {
		//Log.d("sonar", "checkRate()");
		int record = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT); 
		if ((record) < 0) {
			return false;
		}
		return true;
	}
	
	public void setDistFactor(String unit, float sos) {
		//Log.d("sonar", "setUnit()");
		if (unit.equals("ft"))
			distFactor = sos/(float)sRate/2.f*3.2808399f;
		else if (unit.equals("in"))
			distFactor = sos/(float)sRate/2.f*39.3700787f;
		else
			distFactor = sos/(float)sRate/2.f;
	}
	
	public void setCamMic(boolean cm) {
		camMic = cm;
	}
	
	private int getMaxRate() {
		//Log.d("sonar", "getMaxRate()");
		int rate = AudioTrack.getNativeOutputSampleRate(sType);
		if (checkRate(rate))
			return rate;
		for (int i = 0; i < possibleRates.length; i++) {
			rate = possibleRates[i];
			if (checkRate(rate))
				return rate;
		}
		return -1;
			
	}
	
	private void buildChirp(short[] buffer, short[] chirp_sequence) {
		//Log.d("sonar", "buildChirp()");
		for (int i = 0; i < bSize; i++) {
			//create a sine with sweeping frequency: sin(2 Pi f(t) * t)
			//The sweep goes from the (carrier - bandwidth/2) to (carrier + bandwidth/2): f(t) = carrierFreq + bandwidth*(t/T-0.5)
			//Finally T = bSize / sRate
			//and t = i / sRate
			//The sine is then scaled to the size of "short" and stored in the buffer
			buffer[i] = (short)(Short.MAX_VALUE * Math.sin(2*Math.PI*(carrierFreq + bandwidth*(i/(double)bSize-0.5))*i/(double)sRate));
		}
		for (int i = 0; i < bsSize; i++) {
			if ((i % sPeriod) < bSize)
				chirp_sequence[i] = buffer[i%sPeriod];
			else
				chirp_sequence[i] = 0;
		}
	}
	
	public float[] ping() {
		//Log.d("sonar", "ping()");
		//Log.d("sonar", "---GALAXY S FREEZE AHEAD?---");		
		int recRes = 0;
		
		//Log.d("sonar", "Creating AudioRecord");
		int source;
		ar = null;
		if (camMic) {
			try {
				source = MediaRecorder.AudioSource.CAMCORDER;
				ar = new AudioRecord(source, sRate, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT, brSizeInc*2);
			} catch (Exception e) {
			} finally {
			}
		}
		if (ar == null) {
			try {
				source = MediaRecorder.AudioSource.MIC;
				ar = new AudioRecord(source, sRate, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT, brSizeInc*2);
			} catch (Exception e) {
			} finally {
			}
		}
		if (ar == null) {
			error = -4;
			return null;
		}
		
		//Log.d("sonar", "Changing to high priority");
//		Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
		if (!first) {
			//Log.d("sonar", "Not the first call, reloading audio data");
			at.reloadStaticData();
		} else {
			//Log.d("sonar", "First call, not reloading audio data");
			first = false;
		}
		//Log.d("sonar", "Starting recording");
		try {
			ar.startRecording();
		} catch (Exception e) {
			error = -6;
			return null;
		} finally {
		}
		//Log.d("sonar", "Starting audio track -> play");
		at.play();
		//Log.d("sonar", "Wait for brSize * 1000 / sRate = " + brSize * 1000 / sRate + "ms");
		try {
			this.wait(brSize * 100 / sRate);
		} catch (Exception e) {
			//Log.d("sonar", "WARNING: Wait exception!");
		}
		
		//Log.d("sonar", "Reading recording buffer");
		int tempRes = 1;
		while (tempRes > 0 && recRes < brSize) {
			tempRes = ar.read(recording, recRes, brSizeInc-recRes);
			recRes += tempRes;
			try {
				this.wait(brSize * 100 / sRate);
			} catch (Exception e) {
				//Log.d("sonar", "WARNING: Wait exception!");
			}
		}
		
		//Log.d("sonar", "Stopping recording");
		ar.stop();
		
		//Log.d("sonar", "Releasing AudioRecord");
		ar.release();
		ar = null;
		
		//Log.d("sonar", "Changing back to default priority");
//		Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
		
		//Log.d("sonar", "Stopping audio track");
		at.stop();
		
		if (recRes < brSize) {
			//Log.d("sonar", "ERROR: Recording buffer smaller than expected!");
			error = -5;
			error_detail = tempRes;
			return null;
		}
		
		//Log.d("sonar", "Finding first echo and averaging periods");
		averagePeriod(recording, brSize, findBeginning(recording, chirp, addRecordLength*sRate/1000, bSize), periodBuffer, sPeriod);
		//Log.d("sonar", "Calculating cross-correlation");
		crossCorrelate(periodBuffer, chirp, result, sPeriod, bSize, bResSize);
		//Log.d("sonar", "Applying Gaussian filter");
		smooth(result, bResSize, 5);
		//Log.d("sonar", "Normalizing result");
		normalize(result, bResSize, 5, 2*chirpLength*sRate/1000);

		//Log.d("sonar", "Ping is done, returning result.");
		//Log.d("sonar", "---PING() ENDS HERE---");
		return result;
	}
	
	public int getResultSize() {
		//Log.d("sonar", "getResultSize()");
		return bResSize;
	}
	
	public float getMaxRange() {
		//Log.d("sonar", "getMaxRange()");
		return bResSize*distFactor;
	}
	
	public float getMinRange() {
		//Log.d("sonar", "getMinRange()");
		return 2*bSize*distFactor;
	}
	
	public float[][] getLastDistance() {
		//Log.d("sonar", "getLastDistance()");
		return lastDistance;
	}
	
	private void crossCorrelate(float[] f, short[] g, float[] res, int fSize, int gSize, int resSize) { //res has to be of the size fSize-gSize+1 > 0, returns the max of the cross-correlation
		//Log.d("sonar", "crossCorrelate()");
		for (int T = 0; T < resSize; T++) {
			res[T] = 0.f;
			if (T < resSize/3) {
				for (int t = 0; t < gSize; t++) {
					res[T] += f[t+T]*g[t];
				}
			}
		}
	}
	
	private void averagePeriod(short[] rec, int recSize, int zero, float[] pB, int sPeriod) {
		//Log.d("sonar", "averagePeriod()");
		for (int i = 0; i < sPeriod; i++) {
			pB[i] = 0.f;
			for (int j = 0; j < chirpRepeat; j++)
				pB[i] += rec[zero+i+j*sPeriod];
		}
	}
	
	private int findBeginning(short[] rec, short[] chirp, int limit, int bSize) {
		//Log.d("sonar", "findBeginning()");
		float max = 0.f;
		float temp;
		int i = 0;
		for (int T = 0; T < limit; T++) {
			temp = 0.f;
			for (int t = 0; t < bSize; t++) {
				temp += rec[t+T]*chirp[t];
			}
			if (Math.abs(temp) > max) {
				max = Math.abs(temp);
				i = T;
			}
		}
		return i;
	}
	
	private void smooth(float[] buffer, int size, int amount) {
		//Log.d("sonar", "smooth()");
		float[] temp = buffer.clone();
		for (int i = 0; i < size/3; i++) {
			buffer[i] = 0;
			for (int j = -2*amount+i; j <= 2*amount+i; j++)
				if (j >= 0 && j < size)
					buffer[i] += Math.abs(temp[j])*Math.exp(-Math.pow((j-i)/amount, 2)/2.f);
		}
	}
	
	private void normalize(float[] buffer, int size, int amount, int offset) {
		//Log.d("sonar", "normalize()");
		int i, j;
		for (i = 0; i < 5; i++) {
			lastDistance[i][0] = 0.f;
			lastDistance[i][1] = 0.f;
		}
		boolean localMax;
		for (i = offset; i < size/3; i++) {
			localMax = true;
			for (j = -bSize; j <= bSize; j++)
				if (buffer[j+i] > buffer[i])
					localMax = false;
			if (localMax) { //The neighboring peaks are smaller - otherwise skip this point
				j = 5;
				while (j > 0 && lastDistance[j-1][1] < buffer[i]) {
					if (j < 5) {
						lastDistance[j][0] = lastDistance[j-1][0];
						lastDistance[j][1] = lastDistance[j-1][1];
					}
					j--;
				}
				if (j < 5) {
					lastDistance[j][0] = i*distFactor;
					lastDistance[j][1] = buffer[i];
				}
			}
		}

//Normalize me later, when displayed
		
//		for (i = 0; i < size/3; i++)
//			buffer[i] /= lastDistance[0][1];
		
//		for (i = 4; i >= 0; i--)
//			lastDistance[i][1] /= lastDistance[0][1];
	}
}
