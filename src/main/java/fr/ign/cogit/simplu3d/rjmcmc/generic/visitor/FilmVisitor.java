package fr.ign.cogit.simplu3d.rjmcmc.generic.visitor;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.jfree.chart.ChartPanel;

import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IDirectPosition;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IDirectPositionList;
import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
import fr.ign.cogit.geoxygene.contrib.geometrie.Vecteur;
import fr.ign.cogit.geoxygene.feature.DefaultFeature;
import fr.ign.cogit.geoxygene.feature.FT_FeatureCollection;
import fr.ign.cogit.geoxygene.sig3d.gui.InterfaceMap3D;
import fr.ign.cogit.geoxygene.sig3d.gui.MainWindow;
import fr.ign.cogit.geoxygene.sig3d.representation.sample.ObjectCartoon;
import fr.ign.cogit.geoxygene.sig3d.representation.texture.TextureManager;
import fr.ign.cogit.geoxygene.sig3d.representation.texture.TexturedSurface;
import fr.ign.cogit.geoxygene.sig3d.semantic.VectorLayer;
import fr.ign.cogit.geoxygene.spatial.coordgeom.DirectPosition;
import fr.ign.cogit.geoxygene.spatial.coordgeom.DirectPositionList;
import fr.ign.cogit.geoxygene.spatial.coordgeom.GM_LineString;
import fr.ign.cogit.geoxygene.spatial.coordgeom.GM_Polygon;
import fr.ign.cogit.geoxygene.util.attribute.AttributeManager;
import fr.ign.cogit.simplu3d.model.Environnement;
import fr.ign.cogit.simplu3d.representation.RepEnvironnement;
import fr.ign.cogit.simplu3d.representation.RepEnvironnement.Theme;
import fr.ign.cogit.simplu3d.rjmcmc.generic.object.ISimPLU3DPrimitive;
import fr.ign.cogit.simplu3d.util.SimpluParameters;
import fr.ign.mpp.configuration.AbstractBirthDeathModification;
import fr.ign.mpp.configuration.AbstractGraphConfiguration;
import fr.ign.mpp.configuration.GraphVertex;
import fr.ign.rjmcmc.sampler.Sampler;
import fr.ign.simulatedannealing.temperature.Temperature;
import fr.ign.simulatedannealing.visitor.Visitor;

/**
 * 
 * This software is released under the licence CeCILL
 * 
 * see LICENSE.TXT
 * 
 * see  http://www.cecill.info/
 * 
 * 
 * 
 * copyright IGN
 * 
 * @author Brasebin Mickaël
 * 
 * @version 1.0
 **/
public class FilmVisitor<O extends ISimPLU3DPrimitive, C extends AbstractGraphConfiguration<O, C, M>, M extends AbstractBirthDeathModification<O, C, M>>
		implements Visitor<C, M> {

	private MainWindow mW;
	private int save;
	private int iter;

	private final static String PREFIX_NAME_STRING = "Étape";
	private static int MIN_LAYER = 3;

	private C bestConfig = null;
	private double bestValue = Double.POSITIVE_INFINITY;

	private IDirectPosition dp;
	private Vecteur vectOrientation;
	private String folder;
	private int count = 0;
	private Color col;

	public FilmVisitor(IDirectPosition dp, Vecteur vectOrientation, String folder, Color col, SimpluParameters p,
			Environnement env) {
		mW = new MainWindow();
		represent(env, mW, p);
		this.dp = dp;
		this.vectOrientation = vectOrientation;
		this.folder = folder;
		this.col = col;
		
	}

	@Override
	public void init(int dump, int save) {
		this.iter = 0;
		this.save = save;

		mW.getInterfaceMap3D().zoomOn(dp.getX(), dp.getY(), dp.getZ(), vectOrientation);

	}

	@Override
	public void visit(C config, Sampler<C, M> sampler, Temperature t) {
		++iter;

		if (config.getEnergy() < bestValue) {
			bestValue = config.getEnergy();
			bestConfig = config;

		}

		if ((save > 0) && (iter % save == 0)) {
			this.addInformationToMainWindow(config);
		}
	}

	@Override
	public void begin(C config, Sampler<C, M> sampler, Temperature t) {
	}

	@Override
	public void end(C config, Sampler<C, M> sampler, Temperature t) {

		this.addInformationToMainWindow(bestConfig);
	}

	private void addInformationToMainWindow(C config) {

		IFeatureCollection<IFeature> feat = new FT_FeatureCollection<>();

		for (GraphVertex<O> v : config.getGraph().vertexSet()) {

			IGeometry geom = v.getValue().generated3DGeom();

			if (geom == null) {
				continue;
			}

			DefaultFeature df = new DefaultFeature(geom);
			AttributeManager.addAttribute(df, "Energy", v.getEnergy(), "Double");

			df.setRepresentation(new ObjectCartoon(df, col));

			feat.add(df);

		}

		if (!feat.isEmpty()) {
			VectorLayer vl = new VectorLayer(feat, PREFIX_NAME_STRING + " : " + iter);

			int nbLayer = mW.getInterfaceMap3D().getCurrent3DMap().getLayerList().size();

			if (nbLayer > MIN_LAYER) {
				mW.getInterfaceMap3D().getCurrent3DMap().getLayerList().get(nbLayer - 1).setVisible(false);
				mW.getInterfaceMap3D().getCurrent3DMap().removeLayer(
						mW.getInterfaceMap3D().getCurrent3DMap().getLayerList().get(nbLayer - 1).getLayerName());
			}

			mW.getInterfaceMap3D().getCurrent3DMap().addLayer(vl);

		}

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		boolean works = this.screenCapture(folder, "img" + (count++) + ".jpg", mW.getInterfaceMap3D());

		if (!works) {
			System.out.println("Not work");
		}
	}

	/**
	 * Record an image from the 3D Map of the gui. Erase existing file
	 * 
	 * @param path     path in which the screenshot will be sage
	 * @param fileName name of the file
	 * @param iMap3D   the GUI from which the screenshot will be saved
	 * @return a boolean that indicates if the operation is a success
	 */
	public boolean screenCapture(String path, String fileName, InterfaceMap3D iMap3D) {

		try {

			ChartPanel v = StatsVisitor.CHARTSINGLETON;

			int xSup = 0;
			int ySup = 0;

			boolean hasStats = (v != null);

			if (hasStats) {

				xSup = v.getSize().width;
				ySup = v.getSize().height;

			}

			int xSize = iMap3D.getSize().width + xSup;
			int ySize = Math.max(iMap3D.getSize().height, ySup);

			BufferedImage bufImage = new BufferedImage(xSize, ySize, BufferedImage.TYPE_INT_RGB);

			Graphics g = bufImage.createGraphics();

			g.setColor(Color.white);

			// g.drawRect(0, 0, xSize, ySize);

			g.fillRect(0, 0, xSize, ySize);

			iMap3D.getCanvas3D().paint(g);

			if (hasStats) {

				g.drawImage(v.getChart().createBufferedImage(xSup, ySup), iMap3D.getSize().width, (ySize - ySup) / 2,
						null);

			}

			File fichier = new File(path, fileName);
			if (fichier.exists()) {
				System.out.println("Fail");
				return false;
			} else {
				ImageIO.write(bufImage, "jpg", fichier);
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	private void represent(Environnement env, MainWindow mW, SimpluParameters p) {

		List<Theme> lTheme = new ArrayList<>();
		lTheme.add(Theme.TOIT_BATIMENT);
		lTheme.add(Theme.FACADE_BATIMENT);

		lTheme.add(Theme.VOIRIE);
		// lTheme.add(Theme.FAITAGE);
		// lTheme.add(Theme.PIGNON);
		// lTheme.add(Theme.GOUTTIERE);
		// lTheme.add(Theme.VOIRIE);
		lTheme.add(Theme.PARCELLE);
		lTheme.add(Theme.BORDURE);
		lTheme.add(Theme.ZONE);
		// lTheme.add(Theme.PAN)

		Theme[] tab = lTheme.toArray(new Theme[0]);

		List<VectorLayer> vl = RepEnvironnement.represent(env, tab);

		System.out.println("Adding " + vl.size() + " layers");
		for (VectorLayer l : vl) {
			mW.getInterfaceMap3D().getCurrent3DMap().addLayer(l);
		}

		double z = p.getDouble("z");

		double xmin = p.getDouble("xminbg");
		double xmax = p.getDouble("xmaxbg");
		double ymin = p.getDouble("yminbg");
		double ymax = p.getDouble("ymaxbg");

		//
		// 1051042.8513268954120576,6840539.0837931865826249 :
		// 1051264.8064121364150196,6840679.2711814027279615
		// Projet 1
		IDirectPosition dpLL = new DirectPosition(xmin, ymin, z);
		IDirectPosition dpUR = new DirectPosition(xmax, ymax, z);

		// Projet 3
		// IDirectPosition dpLL = new DirectPosition(1051157, 6840727, z);
		// IDirectPosition dpUR = new DirectPosition(1051322, 6840858, z);

		IDirectPositionList dpl = new DirectPositionList();

		IDirectPosition dp2 = new DirectPosition(dpUR.getX(), dpLL.getY(), z);

		IDirectPosition dp4 = new DirectPosition(dpLL.getX(), dpUR.getY(), z);

		dpl.add(dpLL);
		dpl.add(dp2);
		dpl.add(dpUR);
		dpl.add(dp4);
		dpl.add(dpLL);

		IFeatureCollection<IFeature> fc = new FT_FeatureCollection<>();

		IFeature feat = new DefaultFeature(new GM_Polygon(new GM_LineString(dpl)));

		fc.add(feat);

		// feat.setRepresentation(new TexturedSurface(feat, TextureManager
		// .textureLoading(folder + "Env3D_86.png"), dpUR.getX()-dpLL.getX(),
		// dpUR.getY()-dpLL.getY()));

		if(p.getBoolean("showbackground")) {
			String background = p.getString("background_img");
			System.out.println("TUTUTU bckgnd" + background);
			feat.setRepresentation(new TexturedSurface(feat, TextureManager.textureLoading(background),
					dpUR.getX() - dpLL.getX(), dpUR.getY() - dpLL.getY()));

			mW.getInterfaceMap3D().getCurrent3DMap().addLayer(new VectorLayer(fc, "Fond"));
			MIN_LAYER = 2;
		}


		mW.getInterfaceMap3D().removeLight(0);
		mW.getInterfaceMap3D().addLight(new Color(147, 147, 147), 0, 0, 0);
		mW.getInterfaceMap3D().moveLight(dpUR.getX(), dpUR.getY(), 140, 0);

		mW.getInterfaceMap3D().addLight(new Color(147, 147, 147), 0, 0, 0);
		mW.getInterfaceMap3D().moveLight(dpUR.getX(), dpLL.getY(), 140, 1);

		mW.getInterfaceMap3D().addLight(new Color(147, 147, 147), 0, 0, 0);
		mW.getInterfaceMap3D().moveLight(dpLL.getX(), dpUR.getY(), 10000, 2);

		MIN_LAYER = lTheme.size() + 1;

	}
}
