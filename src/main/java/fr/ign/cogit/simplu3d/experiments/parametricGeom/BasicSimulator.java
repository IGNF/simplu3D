package fr.ign.cogit.simplu3d.experiments.parametricGeom;

import java.io.File;

import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.feature.DefaultFeature;
import fr.ign.cogit.geoxygene.feature.FT_FeatureCollection;
import fr.ign.cogit.geoxygene.util.attribute.AttributeManager;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileWriter;
import fr.ign.cogit.simplu3d.demo.DemoEnvironmentProvider;
import fr.ign.cogit.simplu3d.io.nonStructDatabase.shp.LoaderSHP;
import fr.ign.cogit.simplu3d.model.BasicPropertyUnit;
import fr.ign.cogit.simplu3d.model.Environnement;
import fr.ign.cogit.simplu3d.rjmcmc.generic.predicate.SamplePredicate;
import fr.ign.cogit.simplu3d.rjmcmc.paramshapes.factory.footprint.IWallFactory;
import fr.ign.cogit.simplu3d.rjmcmc.paramshapes.factory.roof.IRoofFactory;
import fr.ign.cogit.simplu3d.rjmcmc.paramshapes.impl.ParametricBuilding;
import fr.ign.cogit.simplu3d.rjmcmc.paramshapes.optimizer.ParametricShapeOptimizer;
import fr.ign.mpp.configuration.BirthDeathModification;
import fr.ign.mpp.configuration.GraphConfiguration;
import fr.ign.mpp.configuration.GraphVertex;
import fr.ign.parameters.Parameters;

/**
 * 
 * This software is released under the licence CeCILL
 * 
 * see LICENSE.TXT
 * 
 * see <http://www.cecill.info/ http://www.cecill.info/
 * 
 * 
 * 
 * @copyright IGN
 * 
 * @author Brasebin Mickaël
 * 
 * @version 1.0
 * 
 *          Simulateur standard
 * 
 * 
 */
public class BasicSimulator {

	/**
	 * @param args
	 */

	// [building_footprint_rectangle_cli_main
	public static void main(String[] args) throws Exception {
		
		IRoofFactory roofFactory = null;
		IWallFactory wallFactory= null;

		// Loading of configuration file that contains sampling space
		// information and simulated annealing configuration
		String folderName = BasicSimulator.class.getClassLoader().getResource("scenario/").getPath();
		String fileName = "building_parameters_parametricShape.xml";
		Parameters p = Parameters.unmarshall(new File(folderName + fileName));

		// Load default environment (data are in resource directory)
		Environnement env = LoaderSHP.loadNoDTM(new File(
				DemoEnvironmentProvider.class.getClassLoader().getResource("fr/ign/cogit/simplu3d/data/").getPath()));

		// Select a parcel on which generation is proceeded
		BasicPropertyUnit bPU = env.getBpU().get(8);

		// Instantiation of the sampler
		ParametricShapeOptimizer<ParametricBuilding> oCB = new ParametricShapeOptimizer<ParametricBuilding>();

		// Rules parameters
		// Distance to road
		double distReculVoirie = 0.0;
		// Distance to bottom of the parcel
		double distReculFond = 2;
		// Distance to lateral parcel limits
		double distReculLat = 4;
		// Distance between two buildings of a parcel
		double distanceInterBati = 5;
		// Maximal ratio built area
		double maximalCES = 2;

		// Instantiation of the rule checker
		SamplePredicate<ParametricBuilding, GraphConfiguration<ParametricBuilding>, BirthDeathModification<ParametricBuilding>> pred = new SamplePredicate<>(
				bPU, distReculVoirie, distReculFond, distReculLat, distanceInterBati, maximalCES);

		// Run of the optimisation on a parcel with the predicate
		GraphConfiguration<ParametricBuilding> cc = oCB.process(bPU, p, env, 1,roofFactory, wallFactory, pred);

		// Witting the output
		IFeatureCollection<IFeature> iFeatC = new FT_FeatureCollection<>();
		// For all generated boxes
		for (GraphVertex<ParametricBuilding> v : cc.getGraph().vertexSet()) {

			// Output feature with generated geometry
			IFeature feat = new DefaultFeature(v.getValue().generated3DGeom());

			// We write some attributes
			AttributeManager.addAttribute(feat, "Longueur", Math.max(v.getValue().length, v.getValue().width),
					"Double");
			AttributeManager.addAttribute(feat, "Largeur", Math.min(v.getValue().length, v.getValue().width), "Double");
			AttributeManager.addAttribute(feat, "Hauteur", v.getValue().getHeight(), "Double");
			AttributeManager.addAttribute(feat, "Rotation", v.getValue().orientation, "Double");

			iFeatC.add(feat);

		}

		// A shapefile is written as output
		// WARNING : 'out' parameter from configuration file have to be change
		ShapefileWriter.write(iFeatC, p.get("result").toString() + "out.shp");

		System.out.println("That's all folks");

	}

}