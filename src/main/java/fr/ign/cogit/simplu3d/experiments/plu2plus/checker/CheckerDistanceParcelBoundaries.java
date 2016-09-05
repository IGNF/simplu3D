package fr.ign.cogit.simplu3d.experiments.plu2plus.checker;

import java.util.ArrayList;
import java.util.List;

import fr.ign.cogit.geoxygene.api.spatial.geomaggr.IMultiCurve;
import fr.ign.cogit.geoxygene.api.spatial.geomprim.IOrientableCurve;
import fr.ign.cogit.geoxygene.sig3d.convert.geom.FromGeomToLineString;
import fr.ign.cogit.geoxygene.spatial.geomaggr.GM_MultiCurve;
import fr.ign.cogit.simplu3d.checker.RuleContext;
import fr.ign.cogit.simplu3d.checker.IRuleChecker;
import fr.ign.cogit.simplu3d.checker.UnrespectedRule;
import fr.ign.cogit.simplu3d.model.BasicPropertyUnit;
import fr.ign.cogit.simplu3d.model.Building;
import fr.ign.cogit.simplu3d.model.ParcelBoundary;
import fr.ign.cogit.simplu3d.model.ParcelBoundaryType;

public class CheckerDistanceParcelBoundaries  implements IRuleChecker {
	

	private double dmin;
	private IMultiCurve<IOrientableCurve> ims;

	public CheckerDistanceParcelBoundaries(double dmin, List<ParcelBoundaryType> lTypes, BasicPropertyUnit bPU) {
		super();
		this.dmin = dmin;
		List<ParcelBoundary> lPB = bPU.getCadastralParcels().get(0).getBoundariesByTypes(lTypes);

		ims = new GM_MultiCurve<>();
		for (ParcelBoundary pB : lPB) {
			ims.addAll(FromGeomToLineString.convert(pB.getGeom()));
		}

	}
	@Override
	public List<UnrespectedRule> check(BasicPropertyUnit bPU, RuleContext context) {

		List<UnrespectedRule> lUNR = new ArrayList<UnrespectedRule>();

		List<Building> lBuildings = bPU.getBuildings();

		if (lBuildings.isEmpty()) {
			return lUNR;
		}

		for (Building b : lBuildings) {
			
			double dMeasured = b.getFootprint().distance(ims);

			boolean bool = (dMeasured< dmin);

			if (!bool & context.isStopOnFailure()) {
				lUNR.add(null);
				return lUNR;

			}

			if (!bool) {
				lUNR.add(new UnrespectedRule("Distance minimale non respectée : " + dmin + " > " + dMeasured , b.getGeom(), "Distance"));
			}

		}

		return lUNR;
	}


}
