package fr.ign.cogit.simplu3d.exec;

import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.api.spatial.geomaggr.IMultiSurface;
import fr.ign.cogit.geoxygene.api.spatial.geomprim.IOrientableSurface;
import fr.ign.cogit.geoxygene.feature.DefaultFeature;
import fr.ign.cogit.geoxygene.feature.FT_FeatureCollection;
import fr.ign.cogit.geoxygene.spatial.geomaggr.GM_MultiSurface;
import fr.ign.cogit.geoxygene.util.attribute.AttributeManager;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileWriter;
import fr.ign.cogit.simplu3d.io.load.application.LoaderSHP;
import fr.ign.cogit.simplu3d.model.application.BasicPropertyUnit;
import fr.ign.cogit.simplu3d.model.application.Environnement;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.OptimisedBuildingsCuboidFinalDirectRejection;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.convert.GenerateSolidFromCuboid;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.impl.Cuboid;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.predicate.UXL3Predicate;
import fr.ign.mpp.configuration.GraphConfiguration;
import fr.ign.parameters.Parameters;
import fr.ign.rjmcmc.configuration.Configuration;


/**
 * Simulateur standard
 * 
 * @author MBrasebin
 *
 */
public class BasicSimulator {

  /**
   * @param args
   */

  // [building_footprint_rectangle_cli_main
  public static void main(String[] args) throws Exception {
    String folderName = "./src/main/resources/scenario/";
    String fileName = "building_parameters_project_expthese_3_maison.xml";

    Parameters p = initialize_parameters(folderName + fileName);

    Environnement env = LoaderSHP.load(p.getString("folder"));

    BasicPropertyUnit bPU = env.getBpU().get(1);

    // OCLBuildingsCuboidFinalDirectRejection oCB = new
    // OCLBuildingsCuboidFinalDirectRejection();
    OptimisedBuildingsCuboidFinalDirectRejection oCB = new OptimisedBuildingsCuboidFinalDirectRejection();

    UXL3Predicate<Cuboid> pred = new UXL3Predicate<>(env.getBpU().get(1));

    // UXL3PredicateBuildingSeparation<Cuboid2> pred = new
    // UXL3PredicateBuildingSeparation<>(
    // env.getBpU().get(1));

    // UXL3PredicateGroup<Cuboid2> pred = new
    // UXL3PredicateGroup<Cuboid2>(env.getBpU().get(1),3);

    // UB16PredicateWithParameters<Cuboid2> pred = new
    // UB16PredicateWithParameters<Cuboid2>(bPU ,0,0.5);

    Configuration<Cuboid> cc = oCB.process(bPU, p, env, 1, pred);

    IFeatureCollection<IFeature> iFeatC = new FT_FeatureCollection<>();

    for (GraphConfiguration<Cuboid>.GraphVertex v : ((GraphConfiguration<Cuboid>) cc)
        .getGraph().vertexSet()) {

      IMultiSurface<IOrientableSurface> iMS = new GM_MultiSurface<>();
      iMS.addAll(GenerateSolidFromCuboid.generate(v.getValue()).getFacesList());

      IFeature feat = new DefaultFeature(iMS);

      AttributeManager.addAttribute(feat, "Longueur",
          Math.max(v.getValue().length, v.getValue().width), "Double");
      AttributeManager.addAttribute(feat, "Largeur",
          Math.min(v.getValue().length, v.getValue().width), "Double");
      AttributeManager.addAttribute(feat, "Hauteur", v.getValue().height,
          "Double");
      AttributeManager.addAttribute(feat, "Rotation", v.getValue().orientation,
          "Double");

      iFeatC.add(feat);

    }

    ShapefileWriter.write(iFeatC, p.get("result").toString() + "out.shp");

    System.out.println("That's all folks");

    // OCLBuildingsCuboidFinal oCB = new OCLBuildingsCuboidFinal(); //Rejection
    // sampler => Arrivera t il à proposer une solution ? La réponse dans un
    // prochain épisode

    // OCLBuildingsCuboidFinalWithPredicate oCB = new
    // OCLBuildingsCuboidFinalWithPredicate(); //Exécution de base
    /* Configuration<Cuboid2> cc = */

    // oCB.process(env.getBpU().get(1), p, env, 1);

  }

  private static Parameters initialize_parameters(String name) throws Exception {
    return Parameters.unmarshall(name);
  }
}
