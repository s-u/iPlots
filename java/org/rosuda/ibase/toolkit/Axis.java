package org.rosuda.ibase.toolkit;

import org.rosuda.ibase.*;
import org.rosuda.util.*;

/** Axis - implements transformation of cases, values or categories to orthogonal graphical coordinates and vice versa. Supported axis types are: numerical, equidistant (i.e. nominal/ordinal by index), categorical-equidistant, categorical-proportional by population.
 *
 * @version $Id: Axis.java 2662 2006-10-25 16:41:55Z urbaneks $
 */

public class Axis extends Notifier {
    private static final double PRECISION = 0.0001;
    /** Axis orientation: horizontal (X) */
    public static final int O_Horiz = 0;
    /** Axis orientation: vertical (Y) */
    public static final int O_Vert  = 1;
    /** Axis orientation: horizontal (X) */
    public static final int O_X = 0;
    /** Axis orientation: vertical (Y) */
    public static final int O_Y = 1;
    /** Axis type: numerical */
    public static final int T_Num = 0;
    /** Axis type: equidistant categorical */
    public static final int T_EqCat = 1;
    /** Axis type: proportional categorical */
    public static final int T_PropCat = 2;
    /** Axis type: equidistant (i.e. discrete/nominal/ordinal by index) */
    public static final int T_EqSize = 3;
    
    /** associated variable */
    SVar v;
    /** orientation (see <code>O_..</code> constants) currently: 0=horiz., 1=vert., 2=custom */
    int or;
    
    // those really shouldn't be public, but they get abused quite a lot, so they'll stay public until the code is cleaned out
    
    /** graphical start and length */
    public int gBegin, gLen;
    /** value begin and length */
    public double vBegin, vLen;
    /** count for discrete axes */
    public int datacount;
    
    /** graphical inter-categorial space */
    int gInterSpc=0;
    /** log(vLen) cached */
    double vLenLog10;
    /** type (see <code>T_..</code> constants) currently: 0=numerical,
     * 1=equidistant categories, 2=proportional category population, 3=equidistant */
    int type;
    
    /** gap between categories */
    int gap=0;
    
    /** category sequence */
    SCatSequence seq;
    /** geometry caching sequence */
    AxisCatSequence seqgeom;
    /** orthogonal translation in percent */
    public int trl=0;
    public boolean drawAxis=false;
    
    /** create a new Axis with variable srcv, default orientation (horizontal) and default type guessing and default range
     * @param srcv source variable (cannot be <code>null</code>! for pure numerical axes use {@Axis(SVar,int,int)} constructor!) */
    public Axis(final SVar srcv) {
        v=srcv; or=0; gInterSpc=0;
        type=T_EqSize; // some default type guessing
        if(v!=null) { 
        	if (v.isNum()) type=T_Num;
        	if (v.isCat()) type=T_PropCat;
        	if(v.linked) seq=v.mainSeq();
        	else seq=new SCatSequence(v,this,false);
        	seqgeom=new AxisCatSequence(this, seq);
        }
        setDefaultRange();
    };
    
    /** create new Axis with variable srvc, specified orientation and type and default range
     * @param srcv source variable (can be <code>null</code> if axis type is T_Num or T_EqSize resulting in virtual axis)
     * @param orientation orientation
     * @param axisType axis type */
    public Axis(final SVar srcv, final int orientation, final int axisType) {
        v=srcv; type=axisType; or=orientation; gInterSpc=0;
        if(v!=null) {
        	if(v.linked) seq=v.mainSeq();
        	else seq=new SCatSequence(v,this,false);
        	seqgeom=new AxisCatSequence(this, seq);
        }
        setDefaultRange();
    };
    
    /** return axis orientation
     * @return axis orientation */
    public int getOrientation() {
        return or;
    }
    
    /** change axis type (implicitely calls {@link #setDefaultRange} but preserves
     * cat sequence if switching between "compatible" types, i.e. 1 and 2) */
    public void setType(final int nt) {
        if (nt==type) return;
        boolean reset=true;
        if ((nt==1 && type==T_PropCat)||(nt==2 && type==T_EqCat))
            reset=false;
        type=nt;
        setDefaultRange(reset); // that one calls NotifyAll already
    }
	
	public int getType() {
		return type;
	}
    
    /** set geomery of the axis and notifies dependents upon change
     * @param orientation orientation of the axis (horiz/vert)
     * @param begin begin/anchor of the axis - i.e. the pixel to correspond to {@link #vBegin}
     * @param length length of the axis (note: may be negative if necessary)
     */
    public void setGeometry(final int orientation, final int begin, final int len) {
        if(orientation!=or||begin!=gBegin||len!=gLen) { // lazy notification
            gBegin=begin; gLen=len; or=orientation;
            //if (Global.DEBUG>0) System.out.println("Axis.setGeometry("+orientation+","+begin+","+len+") preformed. ["+this+"] notifying all");
            NotifyAll(new NotifyMsg(this,Common.NM_AxisChange));
        }
    };
    
    /** for numerical variables - set range of the variable's values.
     * @param begin begin/anchor of axis in data domain
     * @param len length of the axis (can be negative if necessary)
     * @return <code>true</code> if this method had any effect on the Axis or <code>false</code> if the values match status quo */
    public boolean setValueRange(final double begin, final double len) {
        if (Global.DEBUG>0) System.out.println("Axis.setValueRange("+begin+","+len+")");
        if (Math.abs(vBegin-begin)>PRECISION || Math.abs(vLen-len)>PRECISION) { // lazy notification
            vBegin=begin; vLen=len;
            vLenLog10=(Math.abs(vLen)<PRECISION)?0:(Math.log((vLen<0)?-vLen:vLen)/Math.log(10));
            NotifyAll(new NotifyMsg(this,Common.NM_AxisChange));
            return true;
        }
        return false;
    }
    
    /** same as {@link #setValueRange(double, double)}, but takes an array of two doubles as returned by {@link #getValueRange} */
    public boolean setValueRange(final double[] range) {
        if (Global.DEBUG>0) System.out.println("Axis.setValueRange(double[2])");
        if (range==null || range.length!=2) return false;
        return setValueRange(range[0],range[1]);
    }
    
    /** for discrete axis types - set the data count
     * @param dc data count, if <1 then set to 1
     * @return <code>true</code> if this method had any effect on the Axis or <code>false</code> if the values match status quo */
    public boolean setValueRange(int dc) {
        if (Global.DEBUG>0) System.out.println("Axis.setValueRange("+dc+")");
        if (dc<1) dc=1;
        if (dc!=datacount) { // lazy notification
            datacount=dc;
            vBegin=0; vLen=dc; // this is necessary if get SensibleTick.. functions are used
            vLenLog10=(Math.abs(vLen)<PRECISION)?0:(Math.log(vLen)/Math.log(10));
            NotifyAll(new NotifyMsg(this,Common.NM_AxisChange));
            return true;
        }
        return false;
    };
    
    /** set default range for the axis (ie for numerical variable min, max are used, for all other types the maixmal count is used. For categorial types this also resets categories sequence to default (ordered by cat ID) - equals to calling @link{#setDefaultRange(boolean) setDefaultRange(true)} */
    public void setDefaultRange() { setDefaultRange(true); };
    
    /** set default range for the axis (i.e. for numerical variable min, max are used, for all other types the maixmal count is used.)
     * @param reseCseq If <code>true</code> for categorial types this also resets categories sequence to default (ordered by cat ID) */
    public void setDefaultRange(final boolean resetCseq) {
        if (v==null) { vBegin=0; vLen=1; return; } // we allow var=null for pure numerical axes, [0:1] is default
        if (v.isNum() && type==T_Num) {
            vBegin=v.getMin();
            vLen=v.getMax()-vBegin;
            vLenLog10=(Math.abs(vLen)<PRECISION)?0:(Math.log((vLen<0)?-vLen:vLen)/Math.log(10));
        } else {
            datacount=v.size();
            vBegin=0; vLen=datacount; // this is necessary for getSensibleTick.. functions etc.
            vLenLog10=(Math.abs(vLen)<PRECISION)?0:(Math.log(vLen)/Math.log(10));
        }
        if (v.isCat() && type==T_EqCat) {
            datacount=v.getNumCats();
            vBegin=0; vLen=datacount; // this is necessary for getSensibleTick.. functions etc.
            vLenLog10=(Math.abs(vLen)<PRECISION)?0:(Math.log(vLen)/Math.log(10));
        }
        if (type==T_PropCat||type==T_EqCat) {
            if (resetCseq) seq.reset();
        }
        NotifyAll(new NotifyMsg(this,Common.NM_AxisChange));
    };
    
    /** returns associated variable. Please note that this can be <code>null</code> for virtual axes */
    public SVar getVariable() {
        return v;
    }
    
    public void setVariable(SVar var) {
    	v=var;
    }
    
    /** get graphical position of case with index i (for categorial vars returns
     * the same as getCatCenter called for the category of the case)
     * @param i index of the case
     * @return graphical position of the case */
    public int getCasePos(final int i) {
        if (type==T_EqSize) return gBegin+(int)(((double)gLen)/((double)datacount)*(i));
        if (type==T_Num) return gBegin+(int)((gLen)*(v.atF(i)-vBegin)/vLen);
        if (type==T_PropCat||type==T_EqCat) return getCatCenter(v.getCatIndex(i));
        return -1;
    };
    
    /** get graphical position of value <code>val</code> (for type=0 and 3 only)
     * @param val value
     * @return graphical position of the value */
    public int getValuePos(final double val) {
        if (type==T_EqSize) return gBegin+(int)(((double)gLen)/((double)datacount)*(val));
        //System.out.println(""+val+" -[vBegin="+vBegin+",vLen="+vLen+"]-> "+(gBegin+(int)(((double)gLen)*(val-vBegin)/vLen)));
        if (type==T_Num) return gBegin+(int)((gLen)*(val-vBegin)/vLen);
        if (type==T_PropCat||type==T_EqCat) return getCatCenter((int)(val+0.5)); // we assume that the supplied value is category index
        return -1;
    };
    
    /** get value corresponding to a geometrical position <code>val</code>
     * (for type=0 and 3 only), hence this is the inverse of {@link #getValuePos}
     * @param pos position on the screen
     * @return value corresponding to the supplied position
     */
    public double getValueForPos(final int pos) {
        if (type==T_EqSize) return (pos-gBegin)*(datacount)/((double)gLen);
        if (type==T_Num) return vBegin+(pos-gBegin)*vLen/((double)gLen);
        return -1;
    };
    
    /** clips supplied graphical value to axis' region
     * @param gv graphical value
     * @return clipped graphical value */
    public int clip(final int gv) {
        return (gLen<0)?((gv>gBegin)?gBegin:((gv<gBegin+gLen)?gBegin+gLen:gv)):((gv<gBegin)?gBegin:((gv>gBegin+gLen)?gBegin+gLen:gv));
    };
    
    /** get lower geometry for category of index i (type 1,2 only)
     * @param i category index
     * @return lower position of the category */
    public int getCatLow(final int i) {
        return seqgeom.getLowerEdgeOfCat(i);
    };
    
    /** get upper geometry for category of index i (type 1,2 only)
     * @param i category index
     * @return upper position of the category */
    public int getCatUp(final int i) {
        return seqgeom.getUpperEdgeOfCat(i);
    };
    
    /** get central geometry for category of index i (just a faster way to get (Low+Up)/2 )
     * @param i category index
     * @return central position of the category */
    public int getCatCenter(final int i) {
        return seqgeom.getCenterOfCat(i);
    };
    
    /** get regular geometry for category of index i (e.g. for parallel coordinates plots)
     * @param i category index
     * @param leftGap left gap
     * @param rightGap right gap
     * @return regular position of the category */
    public int getRegularCatPos(final int i,final int leftGap, final int rightGap) {
        return gBegin + leftGap + (getCatSeqIndex(i)*(gLen-leftGap-rightGap))/(v.getNumCats()-1);
    }
    
    /** get category corresponding to a position on screen (type1 and 2 only)
     * @param pos position
     * @return category ID or -1 on failure (e.g. if not of type 1 or 2) */
    public int getCatByPos(final int pos) {
        return seqgeom.getCatByGeometryPos(pos);
    };
    
    /** swap positions of two categories
     * @param c1 category 1
     * @param c2 category 2
     * @return <code>true</code> on success, <code>false</code> on failure
     * (i.e. some index was out of bounds) */
    public boolean swapCats(final int c1, final int c2) {
        return seq.swapCats(c1,c2);
    };
    
    /** move category to another position in the sequence, all remaining
     * categories between the current and new position will be moved
     * correspondingly
     * @param c category to move
     * @param npos new position in the sequence - it is clipped if necessary,
     * i.e. specifying values <0 will move it to the begining and >=cats will
     * move it to the end of the sequence
     * @return <code>true</code> on success, <code>false</code> on failure */
    public boolean moveCat(final int c, int npos) {
        if (npos<0) npos=0;
        final int cats=v.getNumCats();
        if (npos>=cats) npos=cats-1;
        return seq.moveCatAtPosTo(seq.posOfCat(c),npos);
    };
    
    /** for cat types return the position of a category in the sequence
     * of categories. w/o reordering it's always c. It is often used
     * in conjunction with {@link #moveCat} as npos parameter when destination
     * is also a category
     * @param c category index
     * @return position of the category in the sequence
     */
    public int getCatSeqIndex(final int c) {
        return seq.posOfCat(c);
    }
    
    
    /** Inverse of {@link #getCatSeqIndex}. */
    public int getCatAtSeqIndex(final int c) {
        return seq.catAtPos(c);
    }
    
    /** returns a tick distance that is somewhat "sensible" to be used for
     * ticks given mean required distance. The tick distance will be a power
     * of 10. The result can be used to obtain more sophisticated tick
     * values by simply dividing by 2,4 or 5 - or alternatively multipl.
     * by 2, 2.5 or 5
     * @param medDist mean required distance
     * @param mindist minimal required distance (if set to 0 only powers of 10 will be used)
     * @return proposed tick distance */
    public double getSensibleTickDistance(final int medDist, final int minDist) {
        final double lgLen=(gLen<0)?-gLen:gLen;
        final double lvLen=(vLen<0)?-vLen:vLen;
        double preld=Math.pow(10.0,Math.round(Math.log(lvLen*((double)medDist)/lgLen)/Math.log(10.0)));
        if (minDist<1) return preld;
        // preld (preliminary distance) is the value as returned by previous versions of getSensibleTickDistance
        // some heuristic is used further to try to satisfy the minDist condition, although it's merely a guideline
        // if medDist is too small then values returned can still be bigger than minDist
        int grs=(int)(preld/lvLen*lgLen);
        if (Global.DEBUG>0) System.out.println("Axis.getSensibleTickDistance("+medDist+","+minDist+"): grs="+grs+", preld="+preld);
        while (grs>2*medDist) { grs/=2; preld/=2; }
        if (grs<minDist/3) preld*=5; else
            if (grs<minDist) preld*=2;
        if ((type==T_EqCat || type==T_PropCat) && preld<1.0) return 1.0;
        return preld;
    };
    
    /** returns first visible tick given a tick distance. it is mostly
     * used in conjunction with {@link #getSensibleTickDistance}
     * @param tickDist tick distance
     * @return first visible tick mark
     */
    public double getSensibleTickStart(final double tickDist) {
        if (type==T_EqCat || type==T_PropCat) return 0;
        double ft=tickDist*((int)(vBegin/tickDist));
        if (ft<vBegin) ft+=tickDist;
        return ft;
    };
    
    /** returns string representation of the supplied value, taking into account
     * the range (vLen) to determine how many digits to display behind the fp
     * @param val value to display
     * @return string representation of the value
     */
    public String getDisplayableValue(final double val) {
        final int dac=((2-((int)vLenLog10))<0)?0:(2-((int)vLenLog10));
        return Tools.getDisplayableValue(val,dac);
    };
    
    /** returns category sequence - useful basically for plots that want to sequentially plot categories
     * @return array of indices of categories. do not modify that array */
    /*
    public int[] getCatSequence() {
        return cseq;
    }
     */
    
    /** returns value range as an array of two doubles specifying top and bottom end. to ensure reproducibility the orientation is preserved, therefore it is not guaranteed that the second value is greater that the first one.
     */
    public double[] getValueRange() {
        final double rg[]=new double[2];
        rg[0]=vBegin;
        rg[1]=vBegin+vLen;
        return rg;
    }
    
    /** moves the axis along the orthogonal direction */
    public void setOrthTrans(int trl) {
    	this.trl=trl;
    }
    
    /** somewhat simple toString implementation, basically for debugging purposes */
    public String toString() {
        return "Axis(type="+type+",or="+or+",g["+gBegin+":"+gLen+"],v["+vBegin+":"+vLen+"],trl="+trl+",draw="+drawAxis+",dc="+datacount+(seq!=null?",seq="+seq.toString()+")":")");
    };
};
