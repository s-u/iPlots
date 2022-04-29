package org.rosuda.ibase.plots;

import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.util.*;

import org.rosuda.ibase.*;
import org.rosuda.ibase.toolkit.*;
import org.rosuda.pograss.*;
import org.rosuda.util.*;

/** implementation of scatterplots
 * @version $Id: ScatterCanvas.java 2734 2007-04-25 09:44:28Z wichtrey $
 */
public class ScatterCanvas extends BaseCanvas {
    static final String M_PLUS = "+";
    static final String M_EQUISCALE = "equiscale";
    static final String M_MINUS = "-";
    static final String M_LABELS = "labels";
    static final String M_FIELDBG = "customFieldBg";
    static final String M_XRANGEDLG = "XrangeDlg";
    static final String M_YRANGEDLG = "YrangeDlg";
    static final String M_POINTSUP = "points+";
    static final String M_POINTSDOWN = "points-";
    
    /** array of two variables (X and Y) */
    protected SVar v[];
    
    /** flag whether axis labels should be shown */
    protected boolean showLabels=true;
    
    /**
     * if true partition nodes above current node only
     * @deprecated only used by klimt
     */
    public boolean bgTopOnly=false;
    
    /** diameter of a point */
    public int ptDiam=3;
    
    /** minimal point diameter */
    public int minimalDiam=1;
    
    public boolean customFieldBg=false;
    
    /** # of points */
    protected int pts;
    
    protected int x1, y1, x2, y2;
    protected boolean drag;
    
    protected MenuItem MIlabels=null;
    private MenuItem MIequiscale=null;
    
    protected int Y,W,H, TW,TH;
    
    // needed for axis-query
    private final int[] axcoordX;
    private final int[] axcoordY;
    private final int[] aycoordX;
    private final int[] aycoordY;
    
    protected int []filter=null;
    
    protected boolean zoomRetainsAspect=false;
    
    private final int standardMLeft=40;
    
    /**
     * Custom background color. Defaults to white.
     */
    public Color COL_CUSTOMBG = Color.WHITE;
    
    private boolean crosshairs = false;
    
    private int qx,qy;
    
    /**
     * The number of pixels the point diameter should be changed when in-/decreasing it.
     */
    public int changePtDiamBy=2;
    
    /**
     * Whether axes shoud be drawn.
     */
    public boolean drawAxes = true;
    
    private boolean equiscale = false;
    
    private double spaceprop=1.1;
    
    /** sorted set of the points, used to check with log(n) time cost if a point
     *  belongs to an existing primitive
     */
    protected TreeMap sortedPointsX, sortedPointsY;
    
    private class PointComparator implements Comparator{
	private int type;
	
	public static final int TYPE_X=0;
	public static final int TYPE_Y=1;
	
	public PointComparator(final int type){
	    this.type=type;
	}
	
	public int compare(final Object o1, final Object o2) {
	    final int c1;
	    final int c2;
	    final int d1;
	    final int d2;
	    switch (type){
		case TYPE_X:
		    c1 = ((Point)o1).x;
		    c2 = ((Point)o2).x;
		    d1 = ((Point)o1).y;
		    d2 = ((Point)o2).y;
		    break;
		default:
		    c1 = ((Point)o1).y;
		    c2 = ((Point)o2).y;
		    d1 = ((Point)o1).x;
		    d2 = ((Point)o2).x;
		    break;
	    }
	    if(c1<c2 || (c1==c2 && d1<d2)) return -1;
	    if(c1==c2 && d1==d2) return 0;
	    else return 1;
	}
    }
    
    /** create a new scatterplot
     * @param f associated frame (or <code>null</code> if none)
     * @param v1 variable 1
     * @param v2 variable 2
     * @param mark associated marker */
    public ScatterCanvas(final int gd, final Frame f, final SVar v1, final SVar v2, final SMarker mark) {
	super(gd,f,mark);
	
	setDefaultMargins(new int[] {standardMLeft,10,10,30});
	
	axcoordX=new int[2]; axcoordY=new int[2];
	aycoordX=new int[2]; aycoordY=new int[2];
	
	v=new SVar[2];
	v[0]=v1; v[1]=v2; m=mark;
	ax=new Axis(v[0],Axis.O_X,v[0].isCat()?Axis.T_EqCat:Axis.T_Num); ax.addDepend(this);
	ay=new Axis(v[1],Axis.O_Y,v[1].isCat()?Axis.T_EqCat:Axis.T_Num); ay.addDepend(this);
	setValueRange();
	drag=false;
	
	//if (Global.useAquaBg) fieldBg=2;
	createMenu(f,true,true,true,false,new String[]{
	    "Same Scale",M_EQUISCALE,
	    M_MINUS,
	    "@LHide Labels",M_LABELS,
	    "Change Background",M_FIELDBG,
	    M_MINUS,
	    "Set X Range ...",M_XRANGEDLG,
	    "Set Y Range ...",M_YRANGEDLG,
	    M_MINUS,
	    "Larger Points (up)",M_POINTSUP,
	    "Smaller Points (down)",M_POINTSDOWN,
	});
	MIequiscale=EzMenu.getItem(f,M_EQUISCALE);
	MIlabels=EzMenu.getItem(f,M_LABELS);
	MItransHighl=EzMenu.getItem(f,M_TRANSHIGHL);
	objectClipping=true;
	dontPaint=false;
	
	try {
	    Class c = Class.forName("org.rosuda.iModels.ScatterMenu");
	    if (c != null) {
		Object o = c.newInstance();
		Method m = c.getMethod("setCanvas",new Class[]{this.getClass()});
		if (m != null)
		    m.invoke(o,new Object[]{this});
		
		pop.addSeparator();
		pop.add((Menu)o);
	    }
	} catch (Exception e) {
	}
    }
    
    public SVar getData(final int id) { return (id<0||id>1)?null:v[id]; }
    
    /**
     *
     * @deprecated
     */
    public void setFilter(final int[] f) {
	filter=f;
	setUpdateRoot(1);
	repaint();
    };
    
    /**
     *
     * @deprecated
     */
    public void setFilter(final Vector v) {
	if (v==null) { filter=null; return; };
	filter=new int[v.size()];
	int j=0; while(j<v.size()) { filter[j]=((Integer)v.elementAt(j)).intValue(); j++; };
    };
    
    public void rotate() {
	// no super call because super.rotate is called by super.run
	try {
	    ((Frame) getParent()).setTitle("Scatterplot ("+v[(orientation+1)&1].getName()+" vs "+v[orientation&1].getName()+")");
	} catch (Exception ee) {};
    };
    
    // clipping warnings
    boolean hasLeft, hasTop, hasRight, hasBot;
    
    public void updateObjects() {
	final Dimension Dsize=getSize();
	final int w=Dsize.width;
	final int h=Dsize.height;
	TW=w; TH=h;
	
	final int innerW=w-mLeft-mRight;
	final int innerH=h-mBottom-mTop;
	
	
	
	((orientation==0)?ax:ay).setGeometry(Axis.O_X,mLeft,W=innerW);
	((orientation==0)?ay:ax).setGeometry(Axis.O_Y,h-mBottom,-(H=innerH));
	Y=TH-mBottom-innerH;
	
	hasLeft=hasRight=hasTop=hasBot=false;
	
	pts=v[0].size();
	if (v[1].size()<pts) pts=v[1].size();
	
	sortedPointsX = new TreeMap(new PointComparator(PointComparator.TYPE_X));
	sortedPointsY = new TreeMap(new PointComparator(PointComparator.TYPE_Y));
	
	for (int i=0;i<pts;i++) {
	    final int jx=0;
	    final int jy=0;
	    if ((!v[0].isMissingAt(i) || v[0].isCat()) && (!v[1].isMissingAt(i) || v[1].isCat())) {
		final int x=jx+ax.getCasePos(i);
		final int y=jy+ay.getCasePos(i);
		//pp[i]=null;
		final int oX = (orientation==0)?x:y;
		final int oY = (orientation==0)?y:x;
		if (oX<mLeft) hasLeft=true;
		else if (oY<mTop) hasTop=true;
		else if (oX>w-mRight) hasRight=true;
		else if (oY>h-mBottom) hasBot=true;
		else {
		    PPrimCircle p;
		    if((p=(PPrimCircle)sortedPointsX.get(new Point(x,y)))!=null){
			final int[] newRef = new int[p.ref.length+1];
			System.arraycopy(p.ref, 0, newRef, 0, p.ref.length);
			newRef[p.ref.length] = i;
			p.ref=newRef;
		    } else{
			p=new PPrimCircle();
			if(orientation==0){
			    p.x = x;
			    p.y = y;
			} else{
			    p.x = y;
			    p.y = x;
			}
			p.diam = ptDiam;
			p.ref = new int[] {i};
			p.fillColor = p.borderColor;
			final Point po = new Point(x,y);
			sortedPointsX.put(po, p);
			sortedPointsY.put(po, p);
		    }
		}
	    } else {
		
		//do nothing
		
		// old code, that doesn't work with object clipping
		/*
		// place missings on the other side of the axes
		int x,y;
		if (v[0].isMissingAt(i)) x=mLeft-4; else x=jx+ax.getCasePos(i);
		if (v[1].isMissingAt(i)) y=h-mBottom+4; else y=jy+ay.getCasePos(i);
		final PPrimCircle p=new PPrimCircle();
		if(orientation==0){
		    p.x = x;
		    p.y = y;
		} else{
		    p.x = y;
		    p.y = x;
		}
		p.diam = ptDiam;
		p.ref = new int[] {i};*/
	    }
	};
	final Collection pts = sortedPointsX.values();
	pp = new PlotPrimitive[pts.size()];
	pts.toArray(pp);
	for(int i=0; i<pp.length; i++) setColors((PPrimBase)pp[i]);
	setBoundValues();
    };
    
    public void keyPressed(final KeyEvent e) {
	if (Global.DEBUG>0)
	    System.out.println("ScatterCanvas: "+e);
	if (e.getKeyCode()==KeyEvent.VK_UP && !e.isShiftDown()) {
	    ptDiam+=changePtDiamBy; setUpdateRoot(0);
	    for(int i=0; i<pp.length; i++) if(pp[i]!=null) ((PPrimCircle)pp[i]).diam = ptDiam;
	    repaint();
	}
	if (e.getKeyCode()==KeyEvent.VK_DOWN && ptDiam>=minimalDiam+changePtDiamBy && !e.isShiftDown()) {
	    ptDiam-=changePtDiamBy; setUpdateRoot(0);
	    for(int i=0; i<pp.length; i++) if(pp[i]!=null) ((PPrimCircle)pp[i]).diam = ptDiam;
	    repaint();
	}
	if(e.getKeyCode()==520 && e.getModifiersEx() == (KeyEvent.ALT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK)){
	    for(int i=0; i< pp.length; i++){
		((PPrimCircle)pp[i]).startArc += 5;
	    }
	    setUpdateRoot(0);repaint();
	}
	super.keyPressed(e);
    };
    
    public Object run(final Object o, final String cmd) {
	super.run(o,cmd);
	if (cmd=="labels") {
	    showLabels=!showLabels;
	    MIlabels.setLabel((showLabels)?"Hide labels":"Show labels");
	    setUpdateRoot(0);
	    repaint();
	};
	if (cmd=="rotate") rotate();
	if (cmd==M_POINTSUP) {
	    ptDiam+=changePtDiamBy;
	    for(int i=0; i<pp.length; i++){
		if(pp[i]!=null) ((PPrimCircle)pp[i]).diam = ptDiam;
	    }
	    setUpdateRoot(0); repaint();
	}
	if (cmd==M_POINTSDOWN && ptDiam>minimalDiam+changePtDiamBy) {
	    ptDiam-=changePtDiamBy;
	    for(int i=0; i<pp.length; i++){
		if(pp[i]!=null) ((PPrimCircle)pp[i]).diam = ptDiam;
	    }
	    setUpdateRoot(0); repaint();
	}
	if (cmd==M_EQUISCALE) {
	    setEquiscale(!equiscale);
	}
	if (cmd=="YrangeDlg" || cmd=="XrangeDlg") {
	    final Axis axis=(cmd=="YrangeDlg")?ay:ax;
	    final Dialog d=intDlg=new Dialog(myFrame,(cmd=="YrangeDlg")?"Y range":"X range",true);
	    final IDlgCL ic=new IDlgCL(this);
	    d.setBackground(Color.white);
	    d.setLayout(new BorderLayout());
	    d.add(new SpacingPanel(),BorderLayout.WEST);
	    d.add(new SpacingPanel(),BorderLayout.EAST);
	    final Panel bp=new Panel(); bp.setLayout(new FlowLayout());
	    final Button b;
	    final Button b2;
	    bp.add(b=new Button("OK"));bp.add(b2=new Button("Cancel"));
	    d.add(bp,BorderLayout.SOUTH);
	    d.add(new Label(" "),BorderLayout.NORTH);
	    final Panel cp=new Panel(); cp.setLayout(new FlowLayout());
	    d.add(cp);
	    cp.add(new Label("start: "));
	    final TextField tw=new TextField(""+axis.vBegin,6);
	    final TextField th=new TextField(""+(axis.vBegin+axis.vLen),6);
	    cp.add(tw);
	    cp.add(new Label(", end: "));
	    cp.add(th);
	    d.pack();
	    b.addActionListener(ic);b2.addActionListener(ic);
	    d.setVisible(true);
	    if (!cancel) {
		final double w=Tools.parseDouble(tw.getText());
		final double h=Tools.parseDouble(th.getText());
		axis.setValueRange(w,h-w);
		updateObjects();
		setUpdateRoot(0);
		repaint();
	    }
	    d.dispose();
	}
	if (cmd==M_FIELDBG) { customFieldBg=!customFieldBg; setUpdateRoot(0); repaint(); };
	if (cmd=="resetZoom") { resetZoom(); repaint(); }
	
	return null;
    }
    
    public void paintBack(final PoGraSS g) {
	
	
	final Dimension Dsize=getSize();
	if (Dsize.width!=TW || Dsize.height!=TH)
	    updateObjects();
	
	if (TW<50||TH<50) {
	    g.setColor(COL_INVALID);
	    g.drawLine(0,0,TW,TH);
	    g.drawLine(0,TH,TW,0);
	    return;
	};
	
	if (customFieldBg) {
	    g.setColor(COL_CUSTOMBG);
	    g.fillRect(mLeft,Y,W,H);
	}
	
	g.setColor(COL_OUTLINE);
	if(orientation==0) {
	    setAyCoord(mLeft,Y,mLeft,Y+H);
	    setAxCoord(mLeft,Y+H,mLeft+W,Y+H);
	} else {
	    setAxCoord(mLeft,Y,mLeft,Y+H);
	    setAyCoord(mLeft,Y+H,mLeft+W,Y+H);
	}
	if(drawAxes){
	    g.drawLine(axcoordX[0],axcoordY[0],axcoordX[1],axcoordY[1]);
	    g.drawLine(aycoordX[0],aycoordY[0],aycoordX[1],aycoordY[1]);
	}
	
	startAddingLabels();
	/* draw ticks and labels for X axis */
	{
	    final int ori = (orientation==0)?0:1;
	    final Axis axis = (orientation==0)?ax:ay;
	    final double f=axis.getSensibleTickDistance(horizontalMedDist,horizontalMinDist);
	    double fi=axis.getSensibleTickStart(f);
	    if (Global.DEBUG>1)
		System.out.println("SP.A[0]:"+axis.toString()+", distance="+f+", start="+fi);
	    try {
		while (fi<axis.vBegin+axis.vLen) {
		    final int t=axis.getValuePos(fi);
		    g.drawLine(t,Y+H,t,Y+H+5);
		    if (showLabels)
			xLabels.add(t,Y+H+20,0.5,0,v[ori].isCat()?v[ori].getCatAt((int)(fi+0.5)).toString():                            axis.getDisplayableValue(fi));
		    fi+=f;
		}
	    } catch (Exception pae) { // catch problems (especially in getCatAt being 0)
	    }
	}
	
	/* draw ticks and labels for Y axis */
	{
	    final int ori = (orientation==0)?1:0;
	    final Axis axis = (orientation==0)?ay:ax;
	    
	    if(showLabels){
		if(v[ori].isCat())
		    addYLabels(g,axis,v[ori],true,true);
		else
		    addYLabels(g,axis,true,false);
	    }
	}
	endAddingLabels();
	
    }
    
    public String queryObject(final PlotPrimitive p) {
	final PPrimCircle ppc = (PPrimCircle)p;
	String qs = "";
	final boolean actionExtQuery = isExtQuery;
	final String v0text,v1text;
	if(ppc.ref.length==1){
	    v0text = (v[0].isCat()?v[0].atS(ppc.ref[0]):(""+v[0].atD(ppc.ref[0])));
	    v1text = (v[1].isCat()?v[1].atS(ppc.ref[0]):(""+v[1].atD(ppc.ref[0])));
	} else{
	    if(v[0].isCat()){
		final SortedSet names = catsIn(ppc.ref,0);
		v0text = names.toString();
	    } else{
		final double[] mM0 = minMax(ppc.ref,0);
		v0text="[" + mM0[0] + ", " + mM0[1] + "]";
	    }
	    if(v[1].isCat()){
		final SortedSet names = catsIn(ppc.ref,1);
		v1text = names.toString();
	    } else{
		final double[] mM1 = minMax(ppc.ref,1);
		v1text="[" + mM1[0] + ", " + mM1[1] + "]";
	    }
	}
	if(actionExtQuery) {
	    if(ppc.ref.length==1){
		qs = v[0].getName() + ": " + v0text + "\n"
			+ v[1].getName() + ": " + v1text + "\n"
			+ ppc.ref.length + " case(s) "+
			Tools.getDisplayableValue(100.0*((double)ppc.ref.length) / (double)v[0].size(),2)+
			"% of var, "+
			Tools.getDisplayableValue(100.0*((double)ppc.ref.length) / (double)(v[0].size()+v[1].size()),2)+
			"% of total)"+
			(m.marked()>0?"\n"+getMarked(p)+" selected ("+Tools.getDisplayableValue(100.0*((double)getMarked(p)) / (double)m.marked(),2)+"% of total selected)":"");
	    } else{
		qs =  v[0].getName() + ": " + v0text + "\n"
			+ v[1].getName() + ": " + v1text + "\n"
			+ ppc.ref.length + " case(s) ("+
			Tools.getDisplayableValue(100.0*((double)ppc.ref.length) / (double)v[0].size(),2)+
			"% of var, "+
			Tools.getDisplayableValue(100.0*((double)ppc.ref.length) / (double)(v[0].size()+v[1].size()),2)+
			"% of total)"+
			(m.marked()>0?"\n"+getMarked(p)+" selected ("+Tools.getDisplayableValue(100.0*((double)getMarked(p)) / (double)m.marked(),2)+"% of total selected)":"");
	    }
	} else {
	    if(ppc.ref.length==1){
		qs = v[0].getName() + ": " + v0text + "\n"
			+ v[1].getName() + ": " + v1text + "\n"
			+ ppc.ref.length + " case(s)";
	    } else{
		qs =  v[0].getName() + ": " + v0text + "\n"
			+ v[1].getName() + ": " + v1text + "\n"
			+ ppc.ref.length + " case(s)";
	    }
	}
	
	return qs;
    }
    
    public String queryPlotSpace() {
	return "Values range:\n"+
		v[0].getName() + ": [" + minVal[0] + ", " + maxVal[0] + "]\n" +
		v[1].getName() + ": [" + minVal[1] + ", " + maxVal[1] + "]";
    }
    
    private double[] maxVal,minVal;
    
    private void setBoundValues() {
	if(pp==null || v==null || v.length<2) return;
	if(maxVal==null) maxVal=new double[2];
	if(minVal==null) minVal=new double[2];
	for(int i=0; i<2;i++){
	    minVal[i] = v[i].getMin();
	    maxVal[i] = v[i].getMax();
	}
    }
    
    
    /* TODO: Maybe this can be done faster with the sortedPoints map */
    private double[] minMax(final int[] ref, final int var){
	final double mM[] = new double[2];
	mM[0] = mM[1] = v[var].atD(ref[0]);
	for(int i=1; i<ref.length; i++){
	    final double atD=v[var].atD(ref[i]);
	    if(atD<mM[0]) mM[0]=atD;
	    if(atD>mM[1]) mM[1]=atD;
	}
	return mM;
    }
    
    public void paintPost(final PoGraSS g) {
	if (crosshairs) {
	    g.setColor(COL_OUTLINE);
	    if((orientation&1) == 0){ // no rotation or 180°
		if (qx==ax.clip(qx) && qy==ay.clip(qy)) {
		    g.drawLine(ax.gBegin,qy,ax.gBegin+ax.gLen,qy);
		    g.drawLine(qx,ay.gBegin,qx,ay.gBegin+ay.gLen);
		    g.drawString(ay.getDisplayableValue(ax.getValueForPos(qx)),qx+2,getHeight()-mBottom-2);
		    g.drawString(ay.getDisplayableValue(ay.getValueForPos(qy)),mLeft+2,qy+11);
		}
	    } else {
		if (qx==ay.clip(qx) && qy==ax.clip(qy)) {
		    g.drawLine(qx,ax.gBegin,qx,ax.gBegin+ax.gLen);
		    g.drawLine(ay.gBegin,qy,ay.gBegin+ay.gLen,qy);
		    g.drawString(ax.getDisplayableValue(ay.getValueForPos(qx)),qx+2,getHeight()-mBottom-2);
		    g.drawString(ax.getDisplayableValue(ax.getValueForPos(qy)),mLeft+2,qy+11);
		}
	    }
	}
	super.paintPost(g);
    }
    
    protected PlotPrimitive getFirstPrimitiveContaining(final int x, final int y) {
	// look if there is a point exactly at (x,y)
	final PlotPrimitive p = (PlotPrimitive)sortedPointsX.get(new Point(x,y));
	if(p!=null) return p;
	
	// find the primitive with shortest distance to (x,y)
	final PlotPrimitive[] pps = getPrimitivesContaining(x,y);
	PlotPrimitive fpc = null;
	int shortestDistance = ptDiam*ptDiam;
	for(int i=0; i<pps.length; i++){
	    if(pps[i]!=null){
		final PPrimCircle ppc = (PPrimCircle)pps[i];
		final int px = ppc.x-x;
		final int py = ppc.y-y;
		final int d  = px*px+py*py;
		if(d==1) return ppc;
		else if(d<shortestDistance){
		    shortestDistance=d;
		    fpc = ppc;
		}
	    }
	}
	return fpc;
    }
    
    protected PlotPrimitive[] getPrimitivesContaining(final int x, final int y) {
	final PlotPrimitive[] pps=getPrimitivesIntersecting(new Rectangle(x-ptDiam/2,y-ptDiam/2, ptDiam,ptDiam));
	for(int i=0; i<pps.length; i++){
	    final PPrimCircle ppc = (PPrimCircle)pps[i];
	    final int px = ppc.x-x;
	    final int py = ppc.y-y;
	    if(px*px+py*py > ptDiam*ptDiam/4) pps[i]=null;
	}
	return pps;
    }
    
    protected PlotPrimitive[] getPrimitivesIntersecting(final Rectangle rec) {
	final int x=(orientation==0)?rec.x:rec.y;
	final int y=(orientation==0)?rec.y:rec.x;
	final int w=(orientation==0)?rec.width:rec.height;
	final int h=(orientation==0)?rec.height:rec.width;
	final Point p1 = new Point(x, y);
	final Point p2 = new Point(x+w, y+h);
	final SortedMap subX = sortedPointsX.subMap(p1, p2);
	final SortedMap subY = sortedPointsY.subMap(p1, p2);
	
	final TreeMap subXClone = new TreeMap(subX);
	subXClone.keySet().retainAll(subY.keySet());
	final Collection col = subXClone.values();
	final PlotPrimitive[] ret = new PlotPrimitive[col.size()];
	col.toArray(ret);
	
	return ret;
    }
    
    public void mouseMoved(final MouseEvent ev) {
	super.mouseMoved(ev);
	final boolean ocrosshairs = crosshairs;
	crosshairs = Common.isMoveTrigger(ev);
	qx=ev.getX();
	qy=ev.getY();
	if(crosshairs || crosshairs!=ocrosshairs){
	    setUpdateRoot(3); repaint();
	}
    }
    
    private int getMarked(final PlotPrimitive p){
	return (int)((p.cases())*p.getMarkedProportion(m,-1)+0.5);
    }
    
    private void setAxCoord(final int x1,final int y1,final int x2,final int y2) {
	if(x1<x2) {axcoordX[0]=x1; axcoordX[1]=x2;} else {axcoordX[0]=x2; axcoordX[1]=x1;}
	if(y1<y2) {axcoordY[0]=y1; axcoordY[1]=y2;} else {axcoordY[0]=y2; axcoordY[1]=y1;}
    }
    
    private void setAyCoord(final int x1,final int y1,final int x2,final int y2) {
	if(x1<x2) {aycoordX[0]=x1; aycoordX[1]=x2;} else {aycoordX[0]=x2; aycoordX[1]=x1;}
	if(y1<y2) {aycoordY[0]=y1; aycoordY[1]=y2;} else {aycoordY[0]=y2; aycoordY[1]=y1;}
    }
    
    protected Axis getMouseOverAxis(final int x, final int y) {
	if(x>=axcoordX[0]-2 && x<= axcoordX[1]+2 && y>=axcoordY[0]-2 && y<=axcoordY[1]+2) return ax;
	else if(x>=aycoordX[0]-2 && x<= aycoordX[1]+2 && y>=aycoordY[0]-2 && y<=aycoordY[1]+2) return ay;
	else return null;
    }
    
    protected String getAxisQuery(final int x, final int y) {
//    	System.out.println("x: " + x + ", y: " + y + ", axX: " + axcoordX[0] + ", axY: " + axcoordY[0] + ", ayX: " + aycoordX[0] + ", ayY: " + aycoordY[0]);
	final Axis a=getMouseOverAxis(x,y);
	if(a==null) return null;
	else return "axis name: " + a.getVariable().getName()+
		"\nrange: "+Tools.getDisplayableValue(a.vBegin,2)+" ... "+Tools.getDisplayableValue(a.vBegin+a.vLen,2);
	
    }
    
    /**
     * Get the amount of space around the data points.
     * @return amount of space
     * @see #setSpaceprop(double)
     */
    public double getSpaceprop() {
	return spaceprop;
    }
    
    /**
     * Sets the amount of space around the data points.
     * 1.0 means no space, 1.5 means half as much space around the data as is used for the data itself.
     * {@link #updateObjects()} needs to be called afterwards.
     * @param SPACEPROP New amount of space. Defaults to 1.1.
     */
    public void setSpaceprop(double SPACEPROP) {
	this.spaceprop = SPACEPROP;
	setValueRange();
    }
    
    private void setValueRange() {
	if(equiscale){
	    final double sfx;
	    final double sfy;
	    final double usfx;
	    final double usfy;
	    sfx=((double)ax.gLen)/ax.vLen; usfx=(sfx<0)?-sfx:sfx;
	    sfy=((double)ay.gLen)/ay.vLen; usfy=(sfy<0)?-sfy:sfy;
	    if (usfx<usfy) {
		ay.setValueRange(ay.vBegin,ay.vLen*(usfy/usfx));
	    } else {
		ax.setValueRange(ax.vBegin,ax.vLen*(usfx/usfy));
	    }
	} else{
	    if (!v[0].isCat()) ax.setValueRange(v[0].getMin()-(v[0].getMax()-v[0].getMin())*(spaceprop-1)/2,(v[0].getMax()-v[0].getMin())*spaceprop);
	    if (!v[1].isCat()) ay.setValueRange(v[1].getMin()-(v[1].getMax()-v[1].getMin())*(spaceprop-1)/2,(v[1].getMax()-v[1].getMin())*spaceprop);
	    if (!v[0].isCat() && Math.abs(v[0].getMax()-v[0].getMin())<0.0001) ax.setValueRange(v[0].getMin()-0.5,1);
	    if (!v[1].isCat() && Math.abs(v[1].getMax()-v[1].getMin())<0.0001) ay.setValueRange(v[1].getMin()-0.5,1);
	}
    }
    
    private SortedSet catsIn(int[] ref, int var) {
	final SortedSet sos = new TreeSet();
	for(int i=0;i<ref.length;i++){
	    sos.add(v[var].atS(ref[i]));
	}
	return sos;
    }
    
    public void setEquiscale(final boolean equiscale) {
	this.equiscale = equiscale;
	MIequiscale.setLabel(equiscale?"Individual scale":"Same scale");
	
	setValueRange();
	updateObjects();
	setUpdateRoot(0);
	repaint();
    }
};
