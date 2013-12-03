package fr.ign.cogit.simplu3d.test.rjmcmc.cuboid.predicate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import fr.ign.cogit.geoxygene.api.spatial.geomaggr.IMultiCurve;
import fr.ign.cogit.geoxygene.api.spatial.geomprim.IOrientableCurve;
import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
import fr.ign.cogit.geoxygene.spatial.geomaggr.GM_MultiCurve;
import fr.ign.cogit.simplu3d.model.application.AbstractBuilding;
import fr.ign.cogit.simplu3d.model.application.BasicPropertyUnit;
import fr.ign.cogit.simplu3d.model.application.CadastralParcel;
import fr.ign.cogit.simplu3d.model.application.SpecificCadastralBoundary;
import fr.ign.rjmcmc.configuration.Configuration;
import fr.ign.rjmcmc.configuration.ConfigurationModificationPredicate;
import fr.ign.rjmcmc.configuration.Modification;

public class UXL3PredicateGroup<O extends AbstractBuilding> implements
    ConfigurationModificationPredicate<O> {

  IMultiCurve<IOrientableCurve> curveS;

  public UXL3PredicateGroup(BasicPropertyUnit bPU) {

    List<IOrientableCurve> lCurve = new ArrayList<>();

    for (CadastralParcel cP : bPU.getCadastralParcel()) {
      // for (SubParcel sB : cP.getSubParcel()) {
      for (SpecificCadastralBoundary sCB : cP.getBoundary()) {

        if (sCB.getType() != SpecificCadastralBoundary.INTRA) {
          IGeometry geom = sCB.getGeom();

          if (geom instanceof IOrientableCurve) {
            lCurve.add((IOrientableCurve) geom);

          } else {
            System.out
                .println("Classe UXL3 : quelque chose n'est pas un ICurve");
          }

          // }

        }

      }

    }

    curveS = new GM_MultiCurve<>(lCurve);

  }

  private List<List<O>> createGroupe(List<O> lBatIn) {

    List<List<O>> listGroup = new ArrayList<>();

    while (!lBatIn.isEmpty()) {

      O batIni = lBatIn.remove(0);

      List<O> currentGroup = new ArrayList<>();
      currentGroup.add(batIni);

      int nbElem = lBatIn.size();

      bouclei: for (int i = 0; i < nbElem; i++) {

        for (O batTemp : currentGroup) {

          if (lBatIn.get(i).getFootprint().touches(batTemp.getFootprint())) {

            currentGroup.add(batTemp);
            lBatIn.remove(i);
            i = -1;
            nbElem--;
            continue bouclei;

          }
        }

      }

      listGroup.add(currentGroup);
    }

    return listGroup;
  }

  @Override
  public boolean check(Configuration<O> c, Modification<O, Configuration<O>> m) {

    List<O> lO = m.getBirth();

    O batDeath = null;

    if (!m.getDeath().isEmpty()) {

      batDeath = m.getDeath().get(0);

    }

    for (O ab : lO) {
      // System.out.println("Oh une naissance");

      // Pas vérifié ?

      boolean checked = true;

      checked = ab.prospect(curveS, 0.5, 0);
      if (!checked) {
        return false;
      }

      checked = (ab.getFootprint().distance(curveS) > 5);
      if (!checked) {
        return false;
      }

    }

    List<O> lBatIni = new ArrayList<>();

    Iterator<O> iTBat = c.iterator();

    while (iTBat.hasNext()) {

      O batTemp = iTBat.next();

      if (batTemp == batDeath) {
        continue;
      }

      lBatIni.add(batTemp);

    }

    for (O ab : lO) {

      lBatIni.add(ab);

    }

    List<List<O>> groupes = createGroupe(lBatIni);

    int nbElem = groupes.size();
    for (int i = 0; i < nbElem; i++) {
      for (int j = 0; j < nbElem; j++) {

        if (compareGroup(groupes.get(i), groupes.get(j)) < 3.1) {
          return false;
        }

      }
    }

    return true;

  }

  private double compareGroup(List<O> l1, List<O> l2) {

    double min = Double.POSITIVE_INFINITY;

    for (O o1 : l1) {
      for (O o2 : l2) {

        min = Math.min(o1.footprint.distance(o2.footprint), min);

      }
    }
    return min;

  }

}
