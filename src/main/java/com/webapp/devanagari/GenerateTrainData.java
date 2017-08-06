package com.webapp.devanagari;

import java.awt.Image;

import java.awt.image.PixelGrabber;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Vector;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.webapp.devanagari.storage.StorageService;

import ocrengine.SampleData;
import ocrengine.Segmentation.CCharacter;
import ocrengine.Segmentation.CLine;
import ocrengine.Segmentation.CWord;
import ocrengine.Segmentation.Segment;

@Controller

public class GenerateTrainData {

	private final StorageService storageService;

	private Path rootLocation;

	private String fileName = "sample_5.dat";
	private String folderName = "learning-data-6";

	private Segment segmenter = new Segment();
	private PrintWriter out;
	private Image img;
	private int iw;
	private int ih;
	private int LineNo;
	private int WordNo;
	private int CharNo;
	private int pixels[];
	private int lineCounter = 0;

	private JSONObject folderMapping = null;

	Vector Lines;

	int downSampleLeft;
	int downSampleRight;
	int downSampleTop;
	int downSampleBottom;
	double ratioX;
	double ratioY;
	int pixelMap[];

	@Autowired
	public GenerateTrainData(StorageService storageService) {
		this.storageService = storageService;
		this.rootLocation = Paths.get(
				"/home/bishnu/Documents/workspace-sts-3.8.4.RELEASE/devanagari-character-recognition/" + folderName);
	}

	@RequestMapping("/generate-train")
	public String run() throws IOException, FileNotFoundException {
		this.out = new PrintWriter(
				"/home/bishnu/Documents/workspace-sts-3.8.4.RELEASE/devanagari-character-recognition/" + fileName,
				"UTF8");
		try {
			Files.walk(this.rootLocation, 2).filter(Files::isRegularFile).forEach(path -> addImageToSampleDat(path));
		} catch (IOException e) {
			System.out.println("Please");
		}

		return "home";
	}

	public void addImageToSampleDat(Path path) {
		File file = path.toFile();
		System.out.println(path);
		if (file != null) {

			try {
				img = ImageIO.read(file);
			} catch (IOException e) {
				System.out.println("LAMO Jasto");
			}

			iw = img.getWidth(null);
			ih = img.getHeight(null);
			pixels = new int[iw * ih];
			PixelGrabber pg = new PixelGrabber(img, 0, 0, iw, ih, pixels, 0, iw);
			try {
				pg.grabPixels();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				System.out.println("pg grabPixels exceptions");
			}
			LineNo = 0;
			WordNo = 0;
			CharNo = 0;
			segmenter.setImage(file);
			Lines = segmenter.makeDataSet(pixels, iw, ih);
			CLine TempLine = (CLine) Lines.get(LineNo);
			CWord TempWord = (CWord) TempLine.getWords().get(WordNo);

			try {
				CCharacter TempCharacter = (CCharacter) TempWord.getCharacters().get(CharNo);

				String folderName = path.toAbsolutePath().toString().split("/")[7];

				downSample(img, TempCharacter.getCharacterStart(), TempWord.getShirorekhaIndex(),
						TempCharacter.getCharacterEnd() - TempCharacter.getCharacterStart(),
						TempLine.getLineEnd() - TempWord.getShirorekhaIndex(), iw, ih,
						getCharFromFolderName(folderName));
			} catch (ArrayIndexOutOfBoundsException e) {
				System.out.println("not readed " + path.toString());
			}

		}

	}

	public void downSample(Image fullTextImage, int wstart, int hstart, int wend, int hend, int iw, int ih,
			char letter) {
		PixelGrabber grabber = new PixelGrabber(fullTextImage, 0, 0, iw, ih, true);

		try {
			grabber.grabPixels();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		pixelMap = (int[]) grabber.getPixels();
		SampleData data = new SampleData(letter, 15, 20);
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

		if (lineCounter != 0)
			out.print("\n");

		out.print(data.getLetter() + ":");
		for (int y = 0; y < data.getHeight(); y++) {
			for (int x = 0; x < data.getWidth(); x++) {
				out.print(data.getData(x, y) ? "1" : "0");
			}
		}

		System.out.println("Wrote line : " + lineCounter++);

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

	public char getCharFromFolderName(String folderName) {

		System.out.println("foldername = " + folderName);
		if (this.folderMapping == null) {
			JSONParser parser = new JSONParser();
			Object obj;
			try {
				obj = parser.parse(new FileReader(
						"/home/bishnu/Documents/workspace-sts-3.8.4.RELEASE/devanagari-character-recognition/folder-mapping.json"));
				this.folderMapping = (JSONObject) obj;
				System.out.println(this.folderMapping);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Character = " + this.folderMapping.get(folderName));
		return this.folderMapping.get(folderName).toString().charAt(0);
	}

}
