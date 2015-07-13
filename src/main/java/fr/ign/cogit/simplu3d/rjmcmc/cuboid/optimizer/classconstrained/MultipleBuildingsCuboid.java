package fr.ign.cogit.simplu3d.rjmcmc.cuboid.optimizer.classconstrained;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.random.RandomGenerator;

import com.vividsolutions.jts.algorithm.Angle;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.operation.distance.DistanceOp;

import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IDirectPosition;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IEnvelope;
import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
import fr.ign.cogit.geoxygene.contrib.geometrie.Vecteur;
import fr.ign.cogit.geoxygene.spatial.coordgeom.DirectPosition;
import fr.ign.cogit.geoxygene.util.conversion.AdapterFactory;
import fr.ign.cogit.simplu3d.iauidf.regulation.Regulation;
import fr.ign.cogit.simplu3d.iauidf.tool.BandProduction;
import fr.ign.cogit.simplu3d.model.application.BasicPropertyUnit;
import fr.ign.cogit.simplu3d.model.application.Environnement;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.endTest.StabilityEndTest;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.energy.cuboid2.VolumeUnaryEnergy;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.impl.Cuboid;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.simple.ParallelCuboid;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.simple.SimpleCuboid;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.sampler.GreenSamplerBlockTemperature;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.transformation.ChangeHeight;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.transformation.ChangeLength;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.transformation.ChangeWidth;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.transformation.MoveCuboid;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.transformation.RotateCuboid;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.transformation.birth.ParallelPolygonTransform;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.transformation.birth.TransformToSurface;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.visitor.CSVendStats;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.visitor.CountVisitor;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.visitor.FilmVisitor;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.visitor.ShapefileVisitorCuboid;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.visitor.StatsVisitor;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.visitor.ViewerVisitor;
import fr.ign.mpp.DirectRejectionSampler;
import fr.ign.mpp.DirectSampler;
import fr.ign.mpp.configuration.BirthDeathModification;
import fr.ign.mpp.configuration.GraphConfiguration;
import fr.ign.mpp.kernel.ObjectBuilder;
import fr.ign.mpp.kernel.ObjectSampler;
import fr.ign.mpp.kernel.UniformTypeView;
import fr.ign.parameters.Parameters;
import fr.ign.random.Random;
import fr.ign.rjmcmc.acceptance.Acceptance;
import fr.ign.rjmcmc.acceptance.MetropolisAcceptance;
import fr.ign.rjmcmc.configuration.ConfigurationModificationPredicate;
import fr.ign.rjmcmc.distribution.PoissonDistribution;
import fr.ign.rjmcmc.energy.ConstantEnergy;
import fr.ign.rjmcmc.energy.MinusUnaryEnergy;
import fr.ign.rjmcmc.energy.MultipliesUnaryEnergy;
import fr.ign.rjmcmc.energy.UnaryEnergy;
import fr.ign.rjmcmc.kernel.Kernel;
import fr.ign.rjmcmc.kernel.NullView;
import fr.ign.rjmcmc.kernel.Transform;
import fr.ign.rjmcmc.kernel.Variate;
import fr.ign.rjmcmc.sampler.Sampler;
import fr.ign.simulatedannealing.SimulatedAnnealing;
import fr.ign.simulatedannealing.endtest.EndTest;
import fr.ign.simulatedannealing.endtest.MaxIterationEndTest;
import fr.ign.simulatedannealing.schedule.GeometricSchedule;
import fr.ign.simulatedannealing.schedule.Schedule;
import fr.ign.simulatedannealing.temperature.SimpleTemperature;
import fr.ign.simulatedannealing.visitor.CSVVisitor;
import fr.ign.simulatedannealing.visitor.CompositeVisitor;
import fr.ign.simulatedannealing.visitor.OutputStreamVisitor;
import fr.ign.simulatedannealing.visitor.Visitor;

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
 **/
public class MultipleBuildingsCuboid {

  public GraphConfiguration<Cuboid> process(
      BasicPropertyUnit bpu,
      Parameters p,
      Environnement env,
      int id,
      ConfigurationModificationPredicate<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> pred,
      Regulation r1, Regulation r2, BandProduction bP) throws Exception {
    // Géométrie de l'unité foncière sur laquelle porte la génération
    IGeometry geom = bpu.getpol2D().buffer(1);

    // Définition de la fonction d'optimisation (on optimise en décroissant)
    // relative au volume
    GraphConfiguration<Cuboid> conf = null;

    try {
      conf = create_configuration(p, geom, bpu);
    } catch (Exception e) {
      e.printStackTrace();
    }
    // Création de l'échantilloneur
    Sampler<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> samp = create_sampler(
        Random.random(), p, bpu, pred, r1, r2, bP);
    if (samp == null) {
      return null;
    }

    // Température
    Schedule<SimpleTemperature> sch = create_schedule(p);

    EndTest end = null;
    if (p.getBoolean(("isAbsoluteNumber"))) {
      end = create_end_test(p);
    } else {
      end = create_end_test_stability(p);
    }

    List<Visitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>> list = new ArrayList<>();
    if (p.getBoolean("outputstreamvisitor")) {
      Visitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> visitor = new OutputStreamVisitor<>(
          System.out);
      list.add(visitor);
    }
    if (p.getBoolean("shapefilewriter")) {
      Visitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> shpVisitor = new ShapefileVisitorCuboid<>(
          p.get("result").toString() + id + "/result");
      list.add(shpVisitor);
    }
    if (p.getBoolean("visitorviewer")) {
      ViewerVisitor<Cuboid, GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> visitorViewer = new ViewerVisitor<>(
          "" + id, p);
      list.add(visitorViewer);
    }

    if (p.getBoolean("statsvisitor")) {
      StatsVisitor<Cuboid, GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> statsViewer = new StatsVisitor<>(
          "Énergie");
      list.add(statsViewer);
    }

    if (p.getBoolean("filmvisitor")) {
      IDirectPosition dpCentre = new DirectPosition(
          p.getDouble("filmvisitorx"), p.getDouble("filmvisitory"),
          p.getDouble("filmvisitorz"));
      Vecteur viewTo = new Vecteur(p.getDouble("filmvisitorvectx"),
          p.getDouble("filmvisitorvecty"),
          p.getDouble("filmvisitorvectz"));
      Color c = new Color(p.getInteger("filmvisitorr"),
          p.getInteger("filmvisitorg"), p.getInteger("filmvisitorb"));
      FilmVisitor<Cuboid, GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> visitorViewerFilmVisitor = new FilmVisitor<>(
          dpCentre, viewTo, p.getString("result"), c, p);
      list.add(visitorViewerFilmVisitor);
    }

    if (p.getBoolean("csvvisitorend")) {
      String fileName = p.get("result").toString()
          + p.get("csvfilenamend");
      CSVendStats<Cuboid, GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> statsViewer = new CSVendStats<>(
          fileName);
      list.add(statsViewer);
    }
    if (p.getBoolean("csvvisitor")) {
      String fileName = p.get("result").toString() + p.get("csvfilename");
      // CSVvisitor<Cuboid, GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> statsViewer = new CSVvisitor<>(
      // fileName);
      CSVVisitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> statsViewer = new CSVVisitor<>(
          fileName);
      list.add(statsViewer);
    }
    countV = new CountVisitor<>();
    list.add(countV);
    CompositeVisitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> mVisitor = new CompositeVisitor<>(
        list);
    init_visitor(p, mVisitor);
    /*
     * < This is the way to launch the optimization process. Here, the magic happen... >
     */
    SimulatedAnnealing.optimize(Random.random(), conf, samp, sch, end,
        mVisitor);
    return conf;
  }

  // Initialisation des visiteurs
  // nbdump => affichage dans la console
  // nbsave => sauvegarde en shapefile
  static void init_visitor(
      Parameters p,
      Visitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> v) {
    v.init(p.getInteger("nbdump"), p.getInteger("nbsave"));
  }

  CountVisitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> countV = null;

  public int getCount() {
    return countV.getCount();
  }

  public GraphConfiguration<Cuboid> create_configuration(Parameters p,
      IGeometry geom, BasicPropertyUnit bpu) throws Exception {

    return this.create_configuration(p,
        AdapterFactory.toGeometry(new GeometryFactory(), geom), bpu);

  }

  // Création de la configuration
  /**
   * @param p
   *          paramètres importés depuis le fichier XML
   * @param bpu
   *          l'unité foncière considérée
   * @return la configuration chargée, c'est à dire la formulation énergétique prise en compte
   */
  public GraphConfiguration<Cuboid> create_configuration(Parameters p,
      Geometry geom, BasicPropertyUnit bpu) {
    // Énergie constante : à la création d'un nouvel objet

    double energyCrea = p.getDouble("energy");

    ConstantEnergy<Cuboid, Cuboid> energyCreation = new ConstantEnergy<Cuboid, Cuboid>(
        energyCrea);

    // Énergie constante : pondération de l'intersection
    ConstantEnergy<Cuboid, Cuboid> ponderationVolume = new ConstantEnergy<Cuboid, Cuboid>(
        p.getDouble("ponderation_volume"));
    // Énergie unaire : aire dans la parcelle
    UnaryEnergy<Cuboid> energyVolume = new VolumeUnaryEnergy<Cuboid>();
    // Multiplication de l'énergie d'intersection et de l'aire
    UnaryEnergy<Cuboid> energyVolumePondere = new MultipliesUnaryEnergy<Cuboid>(
        ponderationVolume, energyVolume);

    // On retire de l'énergie de création, l'énergie de l'aire
    UnaryEnergy<Cuboid> u3 = new MinusUnaryEnergy<Cuboid>(energyCreation,
        energyVolumePondere);

    /*
     * // Énergie constante : pondération de la différence ConstantEnergy<Cuboid, Cuboid> ponderationDifference = new ConstantEnergy<Cuboid, Cuboid>(
     * p.getDouble("ponderation_difference_ext")); // On ajoute l'énergie de différence : la zone en dehors de la parcelle UnaryEnergy<Cuboid> u4 = new
     * DifferenceVolumeUnaryEnergy<Cuboid>(geom); UnaryEnergy<Cuboid> u5 = new MultipliesUnaryEnergy<Cuboid>(ponderationDifference, u4); UnaryEnergy<Cuboid>
     * unaryEnergy = new PlusUnaryEnergy<Cuboid>(u3, u5);
     * 
     * // Énergie binaire : intersection entre deux rectangles ConstantEnergy<Cuboid, Cuboid> c3 = new ConstantEnergy<Cuboid,
     * Cuboid>(p.getDouble("ponderation_volume_inter")); BinaryEnergy<Cuboid, Cuboid> b1 = new IntersectionVolumeBinaryEnergy<Cuboid>(); BinaryEnergy<Cuboid,
     * Cuboid> binaryEnergy = new MultipliesBinaryEnergy<Cuboid, Cuboid>(c3, b1);
     */
    // empty initial configuration*/
    GraphConfiguration<Cuboid> conf = new GraphConfiguration<>(u3,
        new ConstantEnergy<Cuboid, Cuboid>(0));
    return conf;
  }

  /**
   * Sampler
   * 
   * @param p
   *          les paramètres chargés depuis le fichier xml
   * @param r
   *          l'enveloppe dans laquelle on génère les positions
   * @return
   * @throws Exception
   */
  public static Sampler<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> create_sampler(
      RandomGenerator rng,
      Parameters p,
      BasicPropertyUnit bpU,
      ConfigurationModificationPredicate<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> pred,
      Regulation r1, Regulation r2, BandProduction bP) throws Exception {
    // Un vecteur ?????
    double minlen = p.getDouble("minlen");
    double maxlen = p.getDouble("maxlen");

    double minwid = p.getDouble("minwid");
    double maxwid = p.getDouble("maxwid");

    double minheight = p.getDouble("minheight");
    double maxheight = p.getDouble("maxheight");

    IEnvelope env = bpU.getGeom().envelope();
    // in multi object situations, we need an object builder for each
    // subtype and a sampler for the supertype (end of file)
    // TODO : bloquer fixer la hauteur max s'il n'y a pas de prospect
    double[] v = new double[] { env.minX(), env.minY(), minlen, minwid, minheight, 0. };
    double[] d = new double[] { env.maxX(), env.maxY(), maxlen, maxwid, maxheight, Math.PI };
    for (int i = 0; i < d.length; i++) {
      d[i] = d[i] - v[i];
    }
    IGeometry geom = r1.getGeomBande().intersection(bP.getLineRoad().buffer(d[3] / 2 + v[3]));

    IGeometry geomBand = null;
    if (r2 != null) {
      geomBand = r2.getGeomBande();
    }

    Transform transformSimple = new TransformToSurface(d, v, geomBand);
    Transform transformParallel = new ParallelPolygonTransform(d, v,
        geom, bP.getLineRoad().toArray());

    Variate variate = new Variate(rng);
    // Probabilité de naissance-morts modifications
    List<Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>> kernels = new ArrayList<>(
        3);
    // KernelFactory<Cuboid, GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> factory = new KernelFactory<>();

    NullView<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> nullView = new NullView<>();

    double p_simple = 0.5;

    pbuilder = new ParallelBuilder(bP.getLineRoad().toArray());
    if (geom != null && !geom.isEmpty()) {
      List<Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>> lKernelsBand1 = new ArrayList<>();
      lKernelsBand1 = getBande1Kernels(variate, nullView, p, transformParallel);
      kernels.addAll(lKernelsBand1);
    } else {
      p_simple = 1;
    }

    if (geomBand != null && !geomBand.isEmpty()) {
      List<Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>> lKernelsBand2 = new ArrayList<>();
      lKernelsBand2 = getBande2Kernels(variate, nullView, p, transformSimple);
      kernels.addAll(lKernelsBand2);
    } else {
      p_simple = 0;
    }

    // Si on ne peut pas construire dans la deuxième bande ni dans la première ça sert à rien de continue
    if (kernels.isEmpty()) {
      return null;
    }

    // When direct sampling (solomon, etc.), what is the prob to choose a
    // simple cuboid
    CuboidSampler objectSampler = new CuboidSampler(rng, p_simple, transformSimple, transformParallel);

    // poisson distribution
    PoissonDistribution distribution = new PoissonDistribution(rng,
        p.getDouble("poisson"));
    DirectSampler<Cuboid, GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> ds = new DirectRejectionSampler<>(
        distribution, objectSampler, pred);

    Acceptance<SimpleTemperature> acceptance = new MetropolisAcceptance<>();
    Sampler<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> s = new GreenSamplerBlockTemperature<>(
        ds, acceptance, kernels);
    return s;
  }

  private static List<Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>> getBande1Kernels(Variate variate,
      NullView<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> nullView, Parameters p, Transform transformParallel) {
    List<Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>> kernels = new ArrayList<>();

    // Kernel de naissance
    UniformTypeView<Cuboid, GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> pView = new UniformTypeView<>(
        ParallelCuboid.class, pbuilder);
    Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> kernel2 = new Kernel<>(
        nullView, pView, variate, variate, transformParallel,
        p.getDouble("pbirthdeath"), p.getDouble("pbirth"));
    kernel2.setName("BirthDeathParallel");
    kernels.add(kernel2);

    // TODO : bloquer le kernel s'il n'y a pas de prospect
    // double amplitudeHeight = p.getDouble("amplitudeHeight");
    // Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> simpleHeight = new Kernel<>(
    // pView, pView, variate, variate, new ChangeHeight(amplitudeHeight), 0.2, 0.5);
    // simpleHeight.setName("ChgHeightP");
    // kernels.add(simpleHeight);

    // Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> simpleMovekernel = new Kernel<>(
    // pView, pView, variate, variate, new MoveCuboid(p.getDouble("amplitudeMove")),
    // 0.2, 0.5);
    // simpleMovekernel.setName("SimpleMoveP");
    // kernels.add(simpleMovekernel);

    // Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> simpleLength = new Kernel<>(
    // pView, pView, variate, variate, new ChangeLength(p.getDouble("amplitudeMaxDim")), 0.2, 0.5);
    // simpleLength.setName("ChgLengthP");
    // kernels.add(simpleLength);

    return kernels;

  }

  private static List<Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>> getBande2Kernels(Variate variate,
      NullView<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> nullView, Parameters p, Transform transformSimple) {

    List<Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>> kernels = new ArrayList<>();

    UniformTypeView<Cuboid, GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> sView = new UniformTypeView<>(
        SimpleCuboid.class, sbuilder);
    // we also need one birthdeath kernel per object subtype
    Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> kernel1 = new Kernel<>(
        nullView, sView, variate, variate, transformSimple,
        p.getDouble("pbirthdeath"), p.getDouble("pbirth"));
    kernel1.setName("BirthDeathSimple");
    kernels.add(kernel1);

    Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> simpleMovekernel = new Kernel<>(
        sView, sView, variate, variate, new MoveCuboid(p.getDouble("amplitudeMove")),
        0.2, 0.5);
    simpleMovekernel.setName("SimpleMove");
    kernels.add(simpleMovekernel);

    Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> simpleRotatekernel = new Kernel<>(
        sView, sView, variate, variate, new RotateCuboid(
            p.getDouble("amplitudeRotate") * Math.PI / 180), 0.2, 0.5);
    simpleRotatekernel.setName("RotateS");
    kernels.add(simpleRotatekernel);

    Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> simpleWidthkernel = new Kernel<>(
        sView, sView, variate, variate,
        new ChangeWidth(p.getDouble("amplitudeMaxDim")), 0.2, 0.5);
    simpleWidthkernel.setName("ChgWidthS");
    kernels.add(simpleWidthkernel);

    Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> simpleLength = new Kernel<>(
        sView, sView, variate, variate, new ChangeLength(p.getDouble("amplitudeMaxDim")), 0.2, 0.5);
    simpleLength.setName("ChgLengthS");
    kernels.add(simpleLength);

    // TODO : bloquer le kernel s'il n'y a pas de prospect
    double amplitudeHeight = p.getDouble("amplitudeHeight");
    Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> simpleHeight = new Kernel<>(
        sView, sView, variate, variate, new ChangeHeight(amplitudeHeight), 0.2, 0.5);
    simpleHeight.setName("ChgHeightS");
    kernels.add(simpleHeight);

    return kernels;

  }

  private static EndTest create_end_test(Parameters p) {
    return new MaxIterationEndTest(p.getInteger("nbiter"));
  }

  private EndTest create_end_test_stability(Parameters p) {
    double loc_deltaconf = p.getDouble("delta");

    return new StabilityEndTest<Cuboid>(p.getInteger("nbiter"),
        loc_deltaconf);
  }

  private Schedule<SimpleTemperature> create_schedule(Parameters p) {
    double coefDef = p.getDouble("deccoef");

    return new GeometricSchedule<SimpleTemperature>(new SimpleTemperature(
        p.getDouble("temp")), coefDef);
  }

  static ObjectBuilder<Cuboid> sbuilder = new ObjectBuilder<Cuboid>() {
    @Override
    public Cuboid build(double[] coordinates) {
      return new SimpleCuboid(coordinates[0], coordinates[1], coordinates[2], coordinates[3], coordinates[4], coordinates[5]);
    }

    @Override
    public int size() {
      return 6;
    }

    @Override
    public void setCoordinates(Cuboid t, double[] val1) {
      val1[0] = t.centerx;
      val1[1] = t.centery;
      val1[2] = t.length;
      val1[3] = t.width;
      val1[4] = t.height;
      val1[5] = t.orientation;
    }
  };

  private static ObjectBuilder<Cuboid> pbuilder;

  public static class ParallelBuilder implements ObjectBuilder<Cuboid> {
    GeometryFactory factory;
    MultiLineString limits;

    public ParallelBuilder(IGeometry[] limits) throws Exception {
      factory = new GeometryFactory();
      LineString[] lineStrings = new LineString[limits.length];
      for (int i = 0; i < limits.length; i++) {
        lineStrings[i] = (LineString) AdapterFactory.toGeometry(factory, limits[i]);
      }
      this.limits = factory.createMultiLineString(lineStrings);
    }

    @Override
    public Cuboid build(double[] coordinates) {
      Coordinate p = new Coordinate(coordinates[0], coordinates[1]);
      DistanceOp op = new DistanceOp(this.limits, factory.createPoint(p));
      Coordinate projected = op.nearestPoints()[0];
      double distance = op.distance();
      double orientation = Angle.angle(p, projected);
      ParallelCuboid result = new ParallelCuboid(coordinates[0], coordinates[1], coordinates[2], distance * 2, coordinates[3], orientation + Math.PI / 2);
//      System.out.println("build " + p);
//      System.out.println("build " + projected);
//      System.out.println("with = " + distance * 2);
//      System.out.println("lengh = " + coordinates[2]);
//      System.out.println("height = " + coordinates[3]);
//      System.out.println(result.toGeometry());
      return result;
    }

    @Override
    public int size() {
      return 4;
    }

    @Override
    public void setCoordinates(Cuboid t, double[] coordinates) {
      ParallelCuboid pc = (ParallelCuboid) t;
      coordinates[0] = pc.centerx;
      coordinates[1] = pc.centery;
      coordinates[2] = pc.length;
      // coordinates[3] = pc.width;
      coordinates[3] = pc.height;
      // coordinates[5] = pc.orientation;
    }
  };

  public static class CuboidSampler implements ObjectSampler<Cuboid> {
    RandomGenerator engine;
    double p_simple;
    Cuboid object;
    Variate variate;
    Transform transformSimple;
    Transform transformParallel;

    public CuboidSampler(RandomGenerator e, double p_simple,
        Transform transformSimple, Transform transformParallel) {
      this.engine = e;
      this.p_simple = p_simple;
      this.transformSimple = transformSimple;
      this.transformParallel = transformParallel;
      this.variate = new Variate(e);
    }

    @Override
    public double sample(RandomGenerator e) {
      double[] val0;
      double[] val1;
      if (engine.nextDouble() < p_simple) {
        val0 = new double[6];
        val1 = new double[6];
        double phi = this.variate.compute(val0, 0);
        double jacob = this.transformSimple.apply(true, val0, val1);
        this.object = sbuilder.build(val1);
        return phi / jacob;
      }
      val0 = new double[4];
      val1 = new double[4];
      double phi = this.variate.compute(val0, 0);
      double jacob = this.transformParallel.apply(true, val0, val1);
      this.object = pbuilder.build(val1);
      return phi / jacob;
    }

    @Override
    public double pdf(Cuboid t) {
      if (SimpleCuboid.class.isInstance(t)) {
        double[] val1 = new double[6];
        sbuilder.setCoordinates(t, val1);
        double[] val0 = new double[6];
        double J10 = this.transformSimple.apply(false, val1, val0);
        double pdf = this.variate.pdf(val0, 0);
        return pdf * J10;
      }
      double[] val1 = new double[4];
      pbuilder.setCoordinates(t, val1);
      double[] val0 = new double[4];
      double J10 = this.transformParallel.apply(false, val1, val0);
      if (J10 == 0) {
        // System.out.println(t);
        return 0;
      }
      double pdf = this.variate.pdf(val0, 0);
      return pdf * J10;
    }

    @Override
    public Cuboid getObject() {
      return this.object;
    }
  }
}
