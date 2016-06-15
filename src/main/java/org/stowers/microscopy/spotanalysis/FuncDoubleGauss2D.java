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


public class FuncDoubleGauss2D extends FuncGauss2D implements MultivariateJacobianFunction {

    double[] data;
    int width;
    int height;

    public FuncDoubleGauss2D(double[] data, int width, int height)  {

        super(data, width, height);

    }


    @Override
    public double func(double[] p, double x, double y) {

        double res1;
        double res2;
        double resb;
//
        double h1x = (x - p[1]);
        double h1y = (y - p[2]);
        double h1s = .5/(p[3]*p[3]);
        double h1 = h1s*(h1x*h1x + h1y*h1y);

        double h2x = (x - p[5]);
        double h2y = (y - p[6]);
        double h2s = .5/(p[7]*p[7]);
        double h2 = h1s*(h1x*h1x + h1y*h1y);

        res1 = p[0]*FastMath.exp(-h1);
        res2 = p[4]*FastMath.exp(-h1);
        resb = res1 + res2 + p[p.length - 1];
        return resb;
    }

//    public double evalG(double x0, double s, double x) {
//
//        double h = (x - x0)/s;
//        double g = FastMath.exp(-.5*h*h);
//        return g;
//    }

}
