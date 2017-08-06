package ocrengine;

import java.awt.Image;
import java.awt.image.PixelGrabber;
import java.io.BufferedReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.io.UnsupportedEncodingException;

import java.util.Vector;

import javax.imageio.ImageIO;
import javax.swing.DefaultListModel;
import ocrengine.Segmentation.CCharacter;
import ocrengine.Segmentation.CLine;
import ocrengine.Segmentation.CWord;
import ocrengine.Segmentation.Segment;

public class Main {

	private String fileName = "sample_5.dat";
	DefaultListModel<SampleData> letterListModel = new DefaultListModel<SampleData>();
	KohonenNetwork net;
	String RecognizedText;
	Vector Lines;
	private Segment segmenter = new Segment();
	private Image img;
	private int iw;
	private int ih;
	private int pixels[];

	int downSampleLeft;
	int downSampleRight;
	int downSampleTop;
	int downSampleBottom;
	double ratioX;
	double ratioY;
	int pixelMap[];

	public Main() {
		System.out.println("Main Constructor");
		this.load();
		System.out.println("Letter List Model size = " + letterListModel.size());
		this.learn();
	}

	public String run(File file) {
		
		RecognizedText = "";

		try {
			img = ImageIO.read(file);
			iw = img.getWidth(null);
			ih = img.getHeight(null);
			pixels = new int[iw * ih];
			PixelGrabber pg = new PixelGrabber(img, 0, 0, iw, ih, pixels, 0, iw);
			pg.grabPixels();
		} catch (InterruptedException e1) {
		} catch (IOException e2) {
		}
		segmenter.setImage(file);
		Lines = segmenter.makeDataSet(pixels, iw, ih);
		for (int i = 0; i < Lines.size(); i++) {
			CLine TempLine = (CLine) (Lines.get(i));

			for (int j = 0; j < TempLine.getWords().size(); j++) {
				Vector Words = TempLine.getWords();
				CWord TempWord = (CWord) Words.get(j);
				for (int k = 0; k < TempWord.getCharacters().size(); k++) {
					Vector Characters = TempWord.getCharacters();
					CCharacter TempCharacter = (CCharacter) Characters.get(k);
					segmenter.boundCharacter(TempLine, TempWord, TempCharacter);

					double input[] = new double[15 * 20];
					int idx = 0;

					SampleData ds = downSample(img, TempCharacter.getCharacterStart(), TempWord.getShirorekhaIndex(),
							TempCharacter.getCharacterEnd() - TempCharacter.getCharacterStart(),
							TempLine.getLineEnd() - TempWord.getShirorekhaIndex(), iw, ih);

					for (int y = 0; y < ds.getHeight(); y++) {
						for (int x = 0; x < ds.getWidth(); x++) {
							input[idx++] = ds.getData(x, y) ? .5 : -.5;
						}
					}

					double normfac[] = new double[1];
					double synth[] = new double[1];

					int best = net.winner(input, normfac, synth);
					char map[] = mapNeurons();
					RecognizedText += map[best];

				}

				RecognizedText += " ";
			}
			RecognizedText += "\n";
		}

		return RecognizedText;
	}

	public void load() {
		
		// f = new FileInputStream( new File("./sample.dat") );
		try {
			FileReader f;// the actual file stream
			BufferedReader r;// used to read the file line by line	
			
			r = new BufferedReader(new InputStreamReader(
					new FileInputStream(
							"/home/bishnu/Documents/workspace-sts-3.8.4.RELEASE/devanagari-character-recognition/" + fileName),
					"UTF8"));
			String line;
			int i = 0;
			letterListModel.clear();
			while ((line = r.readLine()) != null) {
				SampleData ds = new SampleData(line.charAt(0), 15, 20);
				letterListModel.add(i++, ds);
				System.out.println("Line no:" + i);
				int idx = 2;
				for (int y = 0; y < ds.getHeight(); y++) {
					for (int x = 0; x < ds.getWidth(); x++) {
						ds.setData(x, y, line.charAt(idx++) == '1');
					}
				}
			}

			r.close();

		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void learn() {
		int inputNeuron = 20 * 15;
		int outputNeuron = letterListModel.size();

	

		TrainingSet set = new TrainingSet(inputNeuron, outputNeuron);
		set.setTrainingSetCount(letterListModel.size());

		for (int t = 0; t < letterListModel.size(); t++) {
			int idx = 0;
			SampleData ds = (SampleData) letterListModel.getElementAt(t);
			for (int y = 0; y < ds.getHeight(); y++) {
				for (int x = 0; x < ds.getWidth(); x++) {
					set.setInput(t, idx++, ds.getData(x, y) ? .5 : -.5);
				}
			}
		}

		net = new KohonenNetwork(inputNeuron, outputNeuron);
		net.initialize();
		net.setTrainingSet(set);
		net.learn();
	}

	public SampleData downSample(Image fullTextImage, int wstart, int hstart, int wend, int hend, int iw, int ih) {
		PixelGrabber grabber = new PixelGrabber(fullTextImage, 0, 0, iw, ih, true);

		try {
			grabber.grabPixels();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		pixelMap = (int[]) grabber.getPixels();
		SampleData data = new SampleData(' ', 15, 20);
		downSampleLeft = wstart;
		downSampleRight = wend + wstart;
		downSampleTop = hstart + 1;
		downSampleBottom = hstart + hend;

		ratioX = (double) (downSampleRight - downSampleLeft) / (double) data.getWidth();
		ratioY = (double) (downSampleBottom - downSampleTop) / (double) data.getHeight();

		for (int y = 0; y < data.getHeight(); y++) {
			for (int x = 0; x < data.getWidth(); x++) {
				if (downSampleQuadrant(x, y, fullTextImage))
					data.setData(x, y, true);
				else
					data.setData(x, y, false);
			}
		}
		return data;
	}

	protected boolean downSampleQuadrant(int x, int y, Image img) {
		int w = img.getWidth(null);
		int startX = (int) (downSampleLeft + (x * ratioX));
		int startY = (int) (downSampleTop + (y * ratioY));
		int endX = (int) (startX + ratioX);
		int endY = (int) (startY + ratioY);

		for (int yy = startY; yy <= endY; yy++) {
			for (int xx = startX; xx <= endX; xx++) {
				int loc = xx + (yy * w);

				int p = pixelMap[loc];
				int r = 0xff & (p >> 16);
				int g = 0xff & (p >> 8);
				int b = 0xff & (p);
				int intensity = (r + g + b) / 3;
				Boolean white = false;
				if (intensity > 150) {
					// return false;
				} else {
					return true;
				}

			}
		}

		return false;
	}

	char[] mapNeurons() {
		char map[] = new char[letterListModel.size()];
		double normfac[] = new double[1];
		double synth[] = new double[1];

		for (int i = 0; i < map.length; i++)
			map[i] = '?';
		for (int i = 0; i < letterListModel.size(); i++) {
			double input[] = new double[15 * 20];
			int idx = 0;
			SampleData ds = (SampleData) letterListModel.getElementAt(i);
			for (int y = 0; y < ds.getHeight(); y++) {
				for (int x = 0; x < ds.getWidth(); x++) {
					input[idx++] = ds.getData(x, y) ? .5 : -.5;
				}
			}

			int best = net.winner(input, normfac, synth);

			map[best] = ds.getLetter();
		}
		return map;
	}
	
	
	public int getLetterListSize() {
		return letterListModel.getSize();
	}
}
