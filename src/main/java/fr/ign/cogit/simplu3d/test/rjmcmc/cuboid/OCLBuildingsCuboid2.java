package fr.ign.cogit.simplu3d.test.rjmcmc.cuboid;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IDirectPosition;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IEnvelope;
import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
import fr.ign.cogit.geoxygene.contrib.geometrie.Vecteur;
import fr.ign.cogit.geoxygene.spatial.coordgeom.DirectPosition;
import fr.ign.cogit.geoxygene.util.conversion.AdapterFactory;
import fr.ign.cogit.simplu3d.model.application.BasicPropertyUnit;
import fr.ign.cogit.simplu3d.model.application.Environnement;
import fr.ign.cogit.simplu3d.test.rjmcmc.cuboid.endTest.StabilityEndTest;
import fr.ign.cogit.simplu3d.test.rjmcmc.cuboid.energy.cuboid2.DifferenceVolumeUnaryEnergy;
import fr.ign.cogit.simplu3d.test.rjmcmc.cuboid.energy.cuboid2.IntersectionVolumeBinaryEnergy;
import fr.ign.cogit.simplu3d.test.rjmcmc.cuboid.energy.cuboid2.VolumeUnaryEnergy;
import fr.ign.cogit.simplu3d.test.rjmcmc.cuboid.geometry.impl.Cuboid2;
import fr.ign.cogit.simplu3d.test.rjmcmc.cuboid.geometry.loader.LoaderCuboid2;
import fr.ign.cogit.simplu3d.test.rjmcmc.cuboid.transformation.ChangeHeight;
import fr.ign.cogit.simplu3d.test.rjmcmc.cuboid.transformation.ChangeLength;
import fr.ign.cogit.simplu3d.test.rjmcmc.cuboid.transformation.ChangeWidth;
import fr.ign.cogit.simplu3d.test.rjmcmc.cuboid.transformation.MoveCuboid2;
import fr.ign.cogit.simplu3d.test.rjmcmc.cuboid.transformation.RotateCuboid2;
import fr.ign.cogit.simplu3d.test.rjmcmc.cuboid.visitor.CountVisitor;
import fr.ign.cogit.simplu3d.test.rjmcmc.cuboid.visitor.FilmVisitor;
import fr.ign.cogit.simplu3d.test.rjmcmc.cuboid.visitor.ShapefileVisitorCuboid2;
import fr.ign.cogit.simplu3d.test.rjmcmc.cuboid.visitor.StatsV⁮isitor;
import fr.ign.cogit.simplu3d.test.rjmcmc.cuboid.visitor.ViewerVisitor;
import fr.ign.mpp.DirectSampler;
import fr.ign.mpp.configuration.Configuration;
import fr.ign.mpp.configuration.GraphConfiguration;
import fr.ign.mpp.configuration.Modification;
import fr.ign.mpp.kernel.ObjectBuilder;
import fr.ign.mpp.kernel.UniformBirth;
import fr.ign.parameters.Parameters;
import fr.ign.rjmcmc.acceptance.MetropolisAcceptance;
import fr.ign.rjmcmc.distribution.PoissonDistribution;
import fr.ign.rjmcmc.energy.BinaryEnergy;
import fr.ign.rjmcmc.energy.ConstantEnergy;
import fr.ign.rjmcmc.energy.MinusUnaryEnergy;
import fr.ign.rjmcmc.energy.MultipliesBinaryEnergy;
import fr.ign.rjmcmc.energy.MultipliesUnaryEnergy;
import fr.ign.rjmcmc.energy.PlusUnaryEnergy;
import fr.ign.rjmcmc.energy.UnaryEnergy;
import fr.ign.rjmcmc.kernel.Kernel;
import fr.ign.rjmcmc.sampler.Sampler;
import fr.ign.simulatedannealing.SimulatedAnnealing;
import fr.ign.simulatedannealing.endtest.EndTest;
import fr.ign.simulatedannealing.endtest.MaxIterationEndTest;
import fr.ign.simulatedannealing.schedule.GeometricSchedule;
import fr.ign.simulatedannealing.schedule.Schedule;
import fr.ign.simulatedannealing.temperature.SimpleTemperature;
import fr.ign.simulatedannealing.visitor.CompositeVisitor;
import fr.ign.simulatedannealing.visitor.OutputStreamVisitor;
import fr.ign.simulatedannealing.visitor.Visitor;


@Deprecated
public class OCLBuildingsCuboid2<O, C extends Configuration<O>, S extends Sampler<O, C, SimpleTemperature>, V extends Visitor<O, C, SimpleTemperature, S>> {

  private double coeffDec = Double.NaN;

  public OCLBuildingsCuboid2() {

  }

  public void setCoeffDec(double coeffDec) {
    this.coeffDec = coeffDec;
  }

  public Configuration<Cuboid2> process(BasicPropertyUnit bpu, Parameters p,
      Environnement env, int id) {

    // Géométrie de l'unité foncière sur laquelle porte la génération
    IGeometry geom = bpu.generateGeom().buffer(1);

    // Définition de la fonction d'optimisation (on optimise en décroissant)
    // relative au volume
    Configuration<Cuboid2> conf = null;
    try {
      conf = create_configuration(p,
          AdapterFactory.toGeometry(new GeometryFactory(), geom));
    } catch (Exception e) {
      e.printStackTrace();
    }

    // Création de l'échantilloneur
    Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature> samp = create_sampler(
        p, bpu);

    // Température
    Schedule<SimpleTemperature> sch = create_schedule(p);

    int loadExistingConfig = Integer.parseInt(p.get("load_existing_config"));

    if (loadExistingConfig == 1) {

      String configPath = p.get("config_shape_file").toString();

      List<Cuboid2> lCuboid = LoaderCuboid2.loadFromShapeFile(configPath);
      Modification<Cuboid2, Configuration<Cuboid2>> m = new Modification<>();

      for (Cuboid2 c : lCuboid) {

        m.insertBirth(c);

      }

      conf.deltaEnergy(m);

      conf.apply(m);

      ((SimpleGreenSampler<Cuboid2, Configuration<Cuboid2>, PoissonDistribution, SimpleTemperature, UniformBirth<Cuboid2, Configuration<Cuboid2>, Modification<Cuboid2, Configuration<Cuboid2>>>>) samp).cMI
          .update(lCuboid, new ArrayList<Cuboid2>());

    }

    // EndTest<Cuboid2, Configuration<Cuboid2>, SimpleTemperature,
    // Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>> end =
    // create_end_test(p);

    EndTest<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>> end = null;

    if (Boolean.parseBoolean(p.get("isAbsoluteNumber"))) {
      end = create_end_test(p);
    } else {
      end = create_end_test_stability(p);
    }

    List<Visitor<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>>> list = new ArrayList<Visitor<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>>>();

    if (Boolean.parseBoolean(p.get("outputstreamvisitor"))) {
      Visitor<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>> visitor = new OutputStreamVisitor<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>>(
          System.out);
      list.add(visitor);
    }

    if (Boolean.parseBoolean(p.get("shapefilewriter"))) {
      Visitor<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>> shpVisitor = new ShapefileVisitorCuboid2<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>>(
          "result");
      list.add(shpVisitor);
    }

    if (Boolean.parseBoolean(p.get("visitorviewer"))) {
      ViewerVisitor<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>> visitorViewer = new ViewerVisitor<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>>(
          "" + id,p);

      list.add(visitorViewer);
    }

    if (Boolean.parseBoolean(p.get("shapefilewriter"))) {

      IDirectPosition dpCentre = new DirectPosition(Double.parseDouble(p
          .get("filmvisitorx")), Double.parseDouble(p.get("filmvisitory")),
          Double.parseDouble(p.get("filmvisitorz")));

      Vecteur viewTo = new Vecteur(
          Double.parseDouble(p.get("filmvisitorvectx")), Double.parseDouble(p
              .get("filmvisitorvecty")), Double.parseDouble(p
              .get("filmvisitorvectz")));

      Color c = new Color(Integer.parseInt(p.get("filmvisitorr")),
          Integer.parseInt(p.get("filmvisitorg")), Integer.parseInt(p
              .get("filmvisitorb")));

      FilmVisitor<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>> visitorViewerFilmVisitor = new FilmVisitor<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>>(
          dpCentre, viewTo, p.get("result"), c);

      list.add(visitorViewerFilmVisitor);
    }

    if (Boolean.parseBoolean(p.get("statsvisitor"))) {
      StatsV⁮isitor<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>> statsViewer = new StatsV⁮isitor<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>>(
          "Énergie");
      list.add(statsViewer);

    }

    countV = new CountVisitor<>();

    list.add(countV);

    CompositeVisitor<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>> mVisitor = new CompositeVisitor<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>>(
        list);
    init_visitor(p, mVisitor);

    /*
     * < This is the way to launch the optimization process. Here, the magic
     * happen... >
     */
    SimulatedAnnealing.optimize(conf, samp, sch, end, mVisitor);

    return conf;
  }

  // Initialisation des visiteurs
  // nbdump => affichage dans la console
  // nbsave => sauvegarde en shapefile
  static void init_visitor(Parameters p, Visitor<?, ?, ?, ?> v) {
    v.init(Integer.parseInt(p.get("nbdump")), Integer.parseInt(p.get("nbsave")));
  }

  CountVisitor<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>> countV = null;

  public int getCount() {
    return countV.getCount();
  }

  // ]

  // Création de la configuration
  /**
   * 
   * @param p paramètres importés depuis le fichier XML
   * @param bpu l'unité foncière considérée
   * @return la configuration chargée, c'est à dire la formulation énergétique
   *         prise en compte
   */
  public static Configuration<Cuboid2> create_configuration(Parameters p,
      Geometry bpu) {

    // Énergie constante : à la création d'un nouvel objet
    ConstantEnergy<Cuboid2, Cuboid2> energyCreation = new ConstantEnergy<Cuboid2, Cuboid2>(
        Double.parseDouble(p.get("energy")));

    // Énergie constante : pondération de l'intersection
    ConstantEnergy<Cuboid2, Cuboid2> ponderationVolume = new ConstantEnergy<Cuboid2, Cuboid2>(
        Double.parseDouble(p.get("ponderation_volume")));

    // Énergie unaire : aire dans la parcelle
    UnaryEnergy<Cuboid2> energyVolume = new VolumeUnaryEnergy<Cuboid2>();
    // Multiplication de l'énergie d'intersection et de l'aire
    UnaryEnergy<Cuboid2> energyVolumePondere = new MultipliesUnaryEnergy<Cuboid2>(
        ponderationVolume, energyVolume);

    // On retire de l'énergie de création, l'énergie de l'aire
    UnaryEnergy<Cuboid2> u3 = new MinusUnaryEnergy<Cuboid2>(energyCreation,
        energyVolumePondere);

    // Énergie constante : pondération de la différence
    ConstantEnergy<Cuboid2, Cuboid2> ponderationDifference = new ConstantEnergy<Cuboid2, Cuboid2>(
        Double.parseDouble(p.get("ponderation_difference_ext")));
    // On ajoute l'énergie de différence : la zone en dehors de la parcelle
    UnaryEnergy<Cuboid2> u4 = new DifferenceVolumeUnaryEnergy<Cuboid2>(bpu);
    UnaryEnergy<Cuboid2> u5 = new MultipliesUnaryEnergy<Cuboid2>(
        ponderationDifference, u4);
    UnaryEnergy<Cuboid2> unaryEnergy = new PlusUnaryEnergy<Cuboid2>(u3, u5);

    // Énergie binaire : intersection entre deux rectangles
    ConstantEnergy<Cuboid2, Cuboid2> c3 = new ConstantEnergy<Cuboid2, Cuboid2>(
        Double.parseDouble(p.get("ponderation_volume_inter")));
    BinaryEnergy<Cuboid2, Cuboid2> b1 = new IntersectionVolumeBinaryEnergy<Cuboid2>();
    BinaryEnergy<Cuboid2, Cuboid2> binaryEnergy = new MultipliesBinaryEnergy<Cuboid2, Cuboid2>(
        c3, b1);
    // empty initial configuration*/

    Configuration<Cuboid2> conf = new GraphConfiguration<Cuboid2>(unaryEnergy,
        binaryEnergy);

    return conf;
  }

  // ]

  /**
   * Sampler
   * @param p les paramètres chargés depuis le fichier xml
   * @param r l'enveloppe dans laquelle on génère les positions
   * @return
   */
  static Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature> create_sampler(
      Parameters p, BasicPropertyUnit bpU) {

    IEnvelope r = bpU.generateGeom().envelope();

    // Un vecteur ?????

    double mindim = Double.parseDouble(p.get("mindim"));
    double maxdim = Double.parseDouble(p.get("maxdim"));

    double minheight = Double.parseDouble(p.get("minheight"));
    double maxheight = Double.parseDouble(p.get("maxheight"));
    // A priori on redéfini le constructeur de l'objet
    ObjectBuilder<Cuboid2> builder = new ObjectBuilder<Cuboid2>() {
      @Override
      public Cuboid2 build(double[] coordinates) {
        return new Cuboid2(coordinates[0], coordinates[1], coordinates[2],
            coordinates[3], coordinates[4], coordinates[5]);
      }

      @Override
      public int size() {
        return 6;
      }

      @Override
      public void setCoordinates(Cuboid2 t, double[] coordinates) {
        coordinates[0] = t.centerx;
        coordinates[1] = t.centery;
        coordinates[2] = t.length;
        coordinates[3] = t.width;
        coordinates[4] = t.height;
        coordinates[5] = t.orientation;
      }
    };

    // Sampler de naissance
    UniformBirth<Cuboid2, Configuration<Cuboid2>, Modification<Cuboid2, Configuration<Cuboid2>>> birth = new UniformBirth<Cuboid2, Configuration<Cuboid2>, Modification<Cuboid2, Configuration<Cuboid2>>>(
        new Cuboid2(r.minX(), r.minY(), mindim, mindim, minheight, 0),
        new Cuboid2(r.maxX(), r.maxY(), maxdim, maxdim, maxheight, 2 * Math.PI),
        builder);

    // Distribution de poisson
    PoissonDistribution distribution = new PoissonDistribution(
        Double.parseDouble(p.get("poisson")));

    DirectSampler<Cuboid2, Configuration<Cuboid2>, PoissonDistribution, UniformBirth<Cuboid2, Configuration<Cuboid2>, Modification<Cuboid2, Configuration<Cuboid2>>>> ds = new DirectSampler<Cuboid2, Configuration<Cuboid2>, PoissonDistribution, UniformBirth<Cuboid2, Configuration<Cuboid2>, Modification<Cuboid2, Configuration<Cuboid2>>>>(
        distribution, birth);

    // Probabilité de naissance-morts modifications
    List<Kernel<Cuboid2, Configuration<Cuboid2>, Modification<Cuboid2, Configuration<Cuboid2>>>> kernels = new ArrayList<Kernel<Cuboid2, Configuration<Cuboid2>, Modification<Cuboid2, Configuration<Cuboid2>>>>(
        3);
    
    
    kernels.add(Kernel.make_uniform_birth_death_kernel(builder, birth,
        Double.parseDouble(p.get("pbirth")),
        Double.parseDouble(p.get("pdeath"))));

    /*
     * kernels.add(Kernel.make_uniform_modification_kernel(builder, new
     * RectangleScaledEdgeTransform(), 0.4));
     * kernels.add(Kernel.make_uniform_modification_kernel(builder, new
     * RectangleCornerTranslationTransform(), 0.4));
     */

    /*
     * kernels.add(Kernel.make_uniform_modification_kernel(builder, new
     * MoveCuboid2(), 0.2));
     * 
     * kernels.add(Kernel.make_uniform_modification_kernel(builder, new
     * ChangeWidth(), 0.2));
     * 
     * 
     * 
     * kernels.add(Kernel.make_uniform_modification_kernel(builder, new
     * MoveCuboid2(), 0.2));
     * 
     * kernels.add(Kernel.make_uniform_modification_kernel(builder, new
     * ChangeWidth(), 0.2));
     */
    double amplitudeMaxDim = Double.parseDouble(p.get("amplitudeMaxDim"));
    double amplitudeHeight = Double.parseDouble(p.get("amplitudeHeight"));
    double amplitudeMove = Double.parseDouble(p.get("amplitudeMove"));

    double amplitudeRotate = Double.parseDouble(p.get("amplitudeRotate"))
        * Math.PI / 180;

    /*
     * kernels.add(Kernel.make_uniform_modification_kernel(builder, new
     * RotateCuboid2(), 0.2));
     */

    /*
     * 
     * kernels.add(Kernel.make_uniform_modification_kernel(builder, new
     * ChangeWidth(amplitudeMax), 0.2));
     * 
     * kernels.add(Kernel.make_uniform_modification_kernel(builder, new
     * ChangeLength(amplitudeMax), 0.2));
     * 
     * 
     * 
     * 
     * kernels.add(Kernel.make_uniform_modification_kernel(builder, new
     * MoveCuboid2(amplitudeMove), 0.2));
     */

    kernels.add(Kernel.make_uniform_modification_kernel(builder,
        new ChangeWidth(amplitudeMaxDim), 0.2));

    kernels.add(Kernel.make_uniform_modification_kernel(builder,
        new ChangeLength(amplitudeMaxDim), 0.2));

    kernels.add(Kernel.make_uniform_modification_kernel(builder,
        new MoveCuboid2(amplitudeMove), 0.2));

    kernels.add(Kernel.make_uniform_modification_kernel(builder,
        new ChangeHeight(amplitudeHeight), 0.2));

    kernels.add(Kernel.make_uniform_modification_kernel(builder,
        new RotateCuboid2(amplitudeRotate), 0.2));

    Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature> s = new SimpleGreenSampler<Cuboid2, Configuration<Cuboid2>, PoissonDistribution, SimpleTemperature, UniformBirth<Cuboid2, Configuration<Cuboid2>, Modification<Cuboid2, Configuration<Cuboid2>>>>(
        ds, new MetropolisAcceptance<SimpleTemperature>(), kernels, bpU);
    return s;
  }

  private static EndTest<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>> create_end_test(
      Parameters p) {
    return new MaxIterationEndTest<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>>(
        Integer.parseInt(p.get("nbiter")));
  }

  private static EndTest<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>> create_end_test_stability(
      Parameters p) {
    return new StabilityEndTest<Cuboid2, Configuration<Cuboid2>, SimpleTemperature, Sampler<Cuboid2, Configuration<Cuboid2>, SimpleTemperature>>(
        Integer.parseInt(p.get("nbiter")), Double.parseDouble(p.get("delta")));
  }

  private Schedule<SimpleTemperature> create_schedule(Parameters p) {

    double coefDef = 0;

    if (Double.isNaN(this.coeffDec)) {
      coefDef = Double.parseDouble(p.get("deccoef"));
    } else {
      coefDef = this.coeffDec;
    }

    return new GeometricSchedule<SimpleTemperature>(new SimpleTemperature(
        Double.parseDouble(p.get("temp"))), coefDef);
  }

  // ]
}
