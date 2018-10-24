package fr.ign.cogit.simplu3d.util.distribution;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.geotools.referencing.CRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IDirectPositionList;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IPolygon;
import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
import fr.ign.cogit.geoxygene.feature.DefaultFeature;
import fr.ign.cogit.geoxygene.feature.FT_FeatureCollection;
import fr.ign.cogit.geoxygene.sig3d.calculation.parcelDecomposition.OBBBlockDecomposition;
import fr.ign.cogit.geoxygene.spatial.coordgeom.DirectPositionList;
import fr.ign.cogit.geoxygene.spatial.geomaggr.GM_MultiPoint;
import fr.ign.cogit.geoxygene.util.attribute.AttributeManager;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileWriter;
import fr.ign.cogit.geoxygene.util.index.Tiling;

public class ZonePackager {

	// Attribute to store the id_block, it is only used to check if the
	// algorithm
	// works right
	public static String ATTRIBUTE_NAME_GROUP = "id_block";

	// IDPAR is used during simulation to identify the parcels
	// It will serve during the simulation to make the link between
	// simulation results and parcels
	public static String ATTRIBUTE_NAME_ID = "IDPAR";

	// ATTRIBUTE THAT STORE THE NUMBER OF BAND (0 here)
	// IT is used during the simulation
	public static String ATTRIBUTE_NAME_BAND = "B1_T_BANDE";

	// ATTRIBUTE USED TO DETERMINE IF A PARCEL HAS TO BE SIMULATED
	public static String ATT_SIMUL = "simul";

	// OUTPUT SRID
	public static String SRID_END = "EPSG:2154";

	// Define the radius in which parcel are kepts for the context
	public static double CONTEXT_AREA = 0.5;

	/**
	 * 
	 * @param parcelles
	 *            the set of parcels to decompose
	 * @param numberMaxOfSimulatedParcel
	 *            the number of max parcels to simulated
	 * @param areaMax
	 *            the maximal area to consider a parcel as simulable
	 * @return
	 * @throws Exception
	 */
	public static Map<Integer, IFeatureCollection<IFeature>> createParcelGroups(IFeatureCollection<IFeature> parcelles,
			int numberMaxOfSimulatedParcel, double areaMax) throws Exception {

		// Map Integer / Features of the group
		Map<Integer, IFeatureCollection<IFeature>> mapResult = new HashMap<>();

		// Initialisation of spatial index with updates
		parcelles.initSpatialIndex(Tiling.class, true);

		// Initializatino of ID attribut to -1
		parcelles.stream().forEach(x -> setIDBlock(x, -1));
		// Adding missin attributes ID and NAME_BAND set by ATTRIBUTE_NAME_ID
		// and
		// ATTRIBUTE_NAME_BAND attribute name
		parcelles.stream().forEach(x -> generateMissingAttributes(x));

		// Current group ID
		int idCurrentGroup = 0;

		while (!parcelles.isEmpty()) {
			// We get the first parcel and removes it from the list
			IFeature currentParcel = parcelles.get(0);
			parcelles.remove(0);

			// Step 1 : determining the parcel in the same block
			// Collection that will contain a list of parcels in the same block
			IFeatureCollection<IFeature> grapFeatures = new FT_FeatureCollection<>();
			// Initializing the number of parcels
			grapFeatures.add(currentParcel);

			List<IFeature> candidateParcelles = Arrays.asList(currentParcel);

			System.out.println("Generating new block - still " + parcelles.size() + "  elements left");

			// Initialisation of the recursion method that affects ID neighbour
			// by neighbour
			selectByNeighbourdHood(candidateParcelles, parcelles, grapFeatures);

			System.out.println("The block has : " + grapFeatures.size() + "  elements");

			// Step 2 : cutting the block into bag of limited number of parcel
			// In order to have more balanced bags and increase the distribution
			// performances
			System.out.println("Splitting group");
			List<IFeatureCollection<IFeature>> listOfCutUrbanBlocks = determineCutBlocks(grapFeatures, grapFeatures,
					numberMaxOfSimulatedParcel, areaMax);
			for (IFeatureCollection<IFeature> featCollCutUrbanBlock : listOfCutUrbanBlocks) {
				System.out
						.println("---- Group " + idCurrentGroup + " has " + featCollCutUrbanBlock.size() + " elements");
				for (IFeature feat : featCollCutUrbanBlock) {
					setIDBlock(feat, idCurrentGroup);
				}
				mapResult.put(idCurrentGroup, featCollCutUrbanBlock);
				idCurrentGroup++;
			}

		}

		return mapResult;
	}

	/**
	 * This method is used to cut the collection of blocks into sub block and
	 * check the number of simulable parcels. It is a recursive method
	 * 
	 * @param featColl
	 *            the considered set of parcels for a block
	 * @param featCollTotal
	 *            the collection that contains all the parcels
	 * @param numberMaxOfSimulatedParcel
	 *            the number of max parcels to simulated
	 * @param areaMax
	 *            the maximal area
	 * @return
	 * @throws Exception
	 */
	private static List<IFeatureCollection<IFeature>> determineCutBlocks(IFeatureCollection<IFeature> featColl,
			IFeatureCollection<IFeature> featCollTotal, int numberMaxOfSimulatedParcel, double areaMax)
			throws Exception {
		List<IFeatureCollection<IFeature>> results = new ArrayList<>();

		// Is the block empty enough ?
		if (featColl.size() <= numberMaxOfSimulatedParcel) {
			results.add(determineSimulationBLock(featColl, featCollTotal));
			return results;
		}

		// We keep when the area is enough small and if the simule attribute
		// value is
		// true
		long nbOfSimulatedParcel = featColl.getElements().stream().filter(feat -> (feat.getGeom().area() < areaMax))
				.filter(feat -> (Boolean.parseBoolean(feat.getAttribute(ZonePackager.ATT_SIMUL).toString()))).count();

		if (nbOfSimulatedParcel <= numberMaxOfSimulatedParcel) {
			results.add(determineSimulationBLock(featColl, featCollTotal));
			return results;
		}

		List<IFeatureCollection<IFeature>> collections = determine(featColl);
		// We split into two parts and re-apply the method on them
		results.addAll(determineCutBlocks(collections.get(0), featCollTotal, numberMaxOfSimulatedParcel, areaMax));
		results.addAll(determineCutBlocks(collections.get(1), featCollTotal, numberMaxOfSimulatedParcel, areaMax));

		return results;
	}

	/**
	 * Determine the simulation block and get the context arrond the simulable
	 * parcels (CONTEXT_AREA value determines the radisu)
	 * 
	 * @param featColl
	 * @param featCollTotal
	 * @return
	 * @throws CloneNotSupportedException
	 */
	private static IFeatureCollection<IFeature> determineSimulationBLock(IFeatureCollection<IFeature> featColl,
			IFeatureCollection<IFeature> featCollTotal) throws CloneNotSupportedException {

		if (!featCollTotal.hasSpatialIndex()) {
			featCollTotal.initSpatialIndex(Tiling.class, false);
		}

		// InitialGeemetry
		IDirectPositionList dpl = new DirectPositionList();
		for (IFeature feat : featColl) {
			dpl.addAll(feat.getGeom().coord());
		}
		IGeometry area = new GM_MultiPoint(dpl);

		Collection<IFeature> featCollSelect = featCollTotal.select(area.buffer(CONTEXT_AREA));

		IFeatureCollection<IFeature> finalFeatColl = new FT_FeatureCollection<>();
		finalFeatColl.addAll(featColl);

		for (IFeature feat : featCollSelect) {

			if (featColl.contains(feat)) {

				continue;
			}

			DefaultFeature featureFakeClone = new DefaultFeature();
			featureFakeClone.setGeom(feat.getGeom());
			// It is a new context feature we add a false attribute
			AttributeManager.addAttribute(featureFakeClone, ZonePackager.ATTRIBUTE_NAME_ID,
					feat.getAttribute(ZonePackager.ATTRIBUTE_NAME_ID), "String");
			AttributeManager.addAttribute(featureFakeClone, ZonePackager.ATT_SIMUL, "false", "String");
			AttributeManager.addAttribute(featureFakeClone, ZonePackager.ATTRIBUTE_NAME_BAND, 0, "Integer");

			finalFeatColl.add(featureFakeClone);

		}

		return finalFeatColl;
	}

	/**
	 * Determijne the splitting of a set of parcels into a subset of 2 set of
	 * parcels
	 * 
	 * @param featColl
	 * @return
	 * @throws Exception
	 */
	private static List<IFeatureCollection<IFeature>> determine(IFeatureCollection<IFeature> featColl)
			throws Exception {

		// We make two collection that contains different features
		IFeatureCollection<IFeature> collection1 = new FT_FeatureCollection<>();
		IFeatureCollection<IFeature> collection2 = new FT_FeatureCollection<>();

		List<IFeatureCollection<IFeature>> featureCollection = new ArrayList<>();
		featureCollection.add(collection1);
		featureCollection.add(collection2);

		// We arbitrary split the block into two parts
		if (!featColl.hasSpatialIndex()) {
			featColl.initSpatialIndex(Tiling.class, false);
		}

		// InitialGeemetry
		IDirectPositionList dpl = new DirectPositionList();
		for (IFeature feat : featColl) {
			dpl.addAll(feat.getGeom().coord());
		}
		IGeometry area = new GM_MultiPoint(dpl);

		int nbIterationMax = 15;

		// It is possible that all the parcels intersect one of the two splitted
		// OBB in the case we split the one that intersects all the parcels and
		// subdivide it
		for (int i = 0; i < nbIterationMax; i++) {

			// instead while(true) { for more robustness
			// We cut in a first direction
			List<IPolygon> poly = OBBBlockDecomposition.computeSplittingPolygon(area, true, 0);

			Collection<IFeature> selection = featColl.select(poly.get(0));

			// All elements are in a same side, we cut in an other direct
			if (selection.size() == featColl.size() || selection.isEmpty()) {
				poly = OBBBlockDecomposition.computeSplittingPolygon(area, false, 0);
				selection = featColl.select(poly.get(0));
			}

			if (selection.size() == featColl.size()) {
				area = poly.get(0);
				continue;
			}

			if (selection.isEmpty()) {
				area = poly.get(1);
				continue;
			}

			collection1.addAll(selection);

			for (IFeature feat : featColl) {
				if (!selection.contains(feat)) {
					collection2.add(feat);
				}
			}

			return featureCollection;
		}

		// The algo does not seem to work, we only but 1 feature in each
		// collection
		System.out.println("Going through this way");
		collection1.add(featColl.get(0));
		featColl.remove(0);
		collection1.addAll(featColl);
		return featureCollection;

	}

	/**
	 * A method that determine the neighbour parcels from candidates
	 * (featCandidates) and remove them from the general parcel collections
	 * (parcelles) and set the value attributeCount for the group. The result is
	 * stored in grapFeatures that will be reused in the different uses of the
	 * recursive method
	 * 
	 * @param featCandidates
	 * @param parcelles
	 * @param attributeCount
	 * @param grapFeatures
	 */
	public static void selectByNeighbourdHood(List<IFeature> featCandidates, IFeatureCollection<IFeature> parcelles,
			IFeatureCollection<IFeature> grapFeatures) {

		for (IFeature currentParcel : featCandidates) {
			// Update of the current grap
			grapFeatures.addUnique(currentParcel);
			// We select the surrounding parcels
			Collection<IFeature> surroundingParcels = parcelles.select(currentParcel.getGeom().buffer(0.1));

			// We only keep features where ID is not set
			List<IFeature> listNotSetSurroundingParcels = surroundingParcels.stream().filter(x -> -1 == getIDBlock(x))
					.collect(Collectors.toList());
			// We set the group value to the features
			listNotSetSurroundingParcels.stream().forEach(x -> setIDBlock(x, 1));

			// We remove the list from existing parcels
			parcelles.removeAll(listNotSetSurroundingParcels);

			if (!listNotSetSurroundingParcels.isEmpty()) {
				// We relaunch with the new selected parcels
				selectByNeighbourdHood(listNotSetSurroundingParcels, parcelles, grapFeatures);
			}
		}

	}

	/**
	 * Create a folder for each entry of the map
	 * 
	 * @param map
	 * @param folderIn
	 */
	public static void exportFolder(Map<Integer, IFeatureCollection<IFeature>> map, String folderIn) {

		(new File(folderIn)).mkdirs();
		// For each key we create a folder with associated features
		map.keySet().parallelStream().forEach(x -> createFolderAndExport(folderIn + x + "/", map.get(x)));
	}

	/**
	 * Create a folder for an entry of the map (the name parcelle.shp is used in
	 * the simulator)
	 * 
	 * @param path
	 * @param features
	 */
	private static void createFolderAndExport(String path, IFeatureCollection<IFeature> features) {
		// We create the folder and store the collection

		// This hint is to ensure that the first item has rules
		// Because the schema of the shapefile export is based on the schema of
		// the
		// first feature
		int nbElem = features.size();
		for (int i = 0; i < nbElem; i++) {
			IFeature feat = features.get(i);
			if (Boolean.parseBoolean(feat.getAttribute(ZonePackager.ATT_SIMUL).toString())) {
				features.remove(i);
				features.getElements().add(0, feat);
				break;
			}
		}

		File f = new File(path);
		f.mkdirs();
		try {
			ShapefileWriter.write(features, path + "parcelle.shp", CRS.decode(ZonePackager.SRID_END));
		} catch (NoSuchAuthorityCodeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FactoryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Get IDBlock value for a feature
	 * 
	 * @param x
	 * @return
	 */
	public static int getIDBlock(IFeature x) {
		return Integer.parseInt(x.getAttribute(ZonePackager.ATTRIBUTE_NAME_GROUP).toString());
	}

	/**
	 * Set IDBlock value for a feature
	 * 
	 * @param x
	 * @param value
	 */
	public static void setIDBlock(IFeature x, int value) {
		AttributeManager.addAttribute(x, ZonePackager.ATTRIBUTE_NAME_GROUP, value, "Integer");
	}

	/**
	 * Adding missing attributes : - the ID is generated from a concatenation of
	 * several attributes - the ATTRIBUTE_NAME_BAND that is set to 0 as there is
	 * only one band regulation
	 * 
	 * @param x
	 */
	private static void generateMissingAttributes(IFeature x) {
		String commune = x.getAttribute("commune").toString();
		String prefix = x.getAttribute("prefixe").toString();
		String section = x.getAttribute("section").toString();
		String numero = x.getAttribute("numero").toString();
		String contenance = x.getAttribute("contenance").toString();
		String id = x.getAttribute("id").toString();

		String idFinal = commune + prefix + section + numero + contenance + id;
		AttributeManager.addAttribute(x, ZonePackager.ATTRIBUTE_NAME_ID, idFinal, "String");

		AttributeManager.addAttribute(x, ZonePackager.ATTRIBUTE_NAME_BAND, 0, "Integer");
	}

}
