package org.stowers.microscopy.spotanalysis;

import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Pair;

/**
 * Created by cjw on 5/26/16.
 */


public class FuncGauss2D implements MultivariateJacobianFunction {

    double[] data;
    int width;
    int height;

    public FuncGauss2D() {

    }

    public FuncGauss2D(double[] data, int width, int height) {

        this.data = data;
        this.width = width;
        this.height = height;

    }

    @Override
    public Pair<RealVector, RealMatrix> value(RealVector p) {

        double[] params = new double[p.getDimension()];
        for (int j = 0; j < params.length; j++) {
            params[j] = p.getEntry(j);
            //System.out.print(params[j] + " , ");
        }
        //System.out.println();

        RealVector gvect = new ArrayRealVector(data.length);
        double[] tp = params.clone();  //vector of parameters used for gradient
        Array2DRowRealMatrix jac = new Array2DRowRealMatrix(data.length, params.length);

        for (int i = 0; i < data.length; i++) {
            double x = i % width;
            double y = i / width;

            double gv = func(params, x, y);
            gvect.setEntry(i, gv);

            for (int j = 0; j < p.getDimension(); j++) {
                double pj = tp[j];
                double dp = 0.1*pj;
                if (dp < .00001) {
                    dp = .00001;
                }
                double u1 = pj - dp;
                double u2 = pj + dp;
                tp[j] = u1;

                double g1 = func(tp, x, y);
                tp[j] = u2;
                double g2 = func(tp, x, y);
                double g = (g2 - g1)/(2.*dp);
                jac.setEntry(i, j, g);
                tp[j] = params[j];

            }

            gvect.append(gv);
        }

        Pair<RealVector, RealMatrix> res = new Pair<>(gvect, jac);
        return res;
    }



    public double func(double[] p, double x, double y) {

        double resb;
//
        double hx = (x - p[1]);
        double hy = (y - p[2]);
        double hs = .5/(p[3]*p[3]);
        double h = hs*(hx*hx + hy*hy);

        resb = p[0]*FastMath.exp(-h) + p[4];
        return resb;
    }

//    public double evalG(double x0, double s, double x) {
//
//        double h = (x - x0)/s;
//        double g = FastMath.exp(-.5*h*h);
//        return g;
//    }

}
