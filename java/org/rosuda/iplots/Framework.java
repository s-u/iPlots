//
//  Framework.java - "glue" between R and iplots
//  (C)2003 Simon Urbanek
//
//  $Id: Framework.java 2831 2007-09-07 16:57:08Z urbanek $

package org.rosuda.iplots;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.rosuda.ibase.*;
import org.rosuda.ibase.toolkit.*;
import org.rosuda.ibase.plots.*;
import org.rosuda.util.*;

/** basic framework interface for bulding interactive
 * statistical programs */

public class Framework implements Dependent, ActionListener {
    List dataset;
    SVarSet cvs;
    int tvctr;
    int dtctr;
    
    /** when this flag is set to <code>true</code> then user-interaction is not desired. Most common use is to set this flag to <code>true</code> when <code>make check</code> is being run. */
    static boolean noInteraction = false;

    public int graphicsEngine = PlotComponent.AWTGrDevID; // default
    
    private BaseCanvas[] plots;
    
    /** initialize framework, create and select a dataset which id called "default".
     * one framework supports multiple datasets, plots etc. so there should be no
     * need for multiple frameworks usually.
     */
    public Framework() {
        Global.AppType=Common.AT_Framework;
        Common.supportsBREAK=true;
        Common.appName="iplots";
        SVar.int_NA=-2147483648;
	SVar.double_NA=Double.longBitsToDouble(0x7ff00000000007a2L); // from src/main/arithmetic.c
        Global.useAquaBg=false; // use aqua look
        Common.backgroundColor=Common.aquaBgColor; // use aqua bg color
        org.rosuda.util.Platform.initPlatform("org.rosuda.iplots.");
        if (Common.breakDispatcher==null) Common.breakDispatcher=new Notifier();
        Common.breakDispatcher.addDepend(this);
        cvs=new SVarSet();
        cvs.setName("default");
        dataset=new ArrayList();
        dataset.add(cvs);
    }
    
    public String getNewTmpVar(final String t) {
        int i=cvs.indexOf(t);
        if (i==-1) return t;
        tvctr++;
        i=cvs.indexOf(t+"."+tvctr);
        if (i==-1) return t+"."+tvctr;
        return "temp."+tvctr;
    }
    
    public String getNewTmpVar() {
        tvctr++;
        return "temp."+tvctr;
    }
    
    /** get current dataset */
    public SVarSet getCurrentSet() { return cvs; };

    /** verify whether a given dataset is current */
    public boolean isSetCurrent(SVarSet s) {
	return (s==null && cvs==null) || (cvs!=null && cvs.equals(s));
    }
    
    /** select dataset by name. the initial dataset created during framework initialization is called "default".
     * @param name name of the dataset
     * @return selected dataset or <code>null</code> if no such dataset was found */
    public SVarSet selectSet(final String name) {
        int i=0;
        while (i<dataset.size()) {
            final SVarSet s=(SVarSet)dataset.get(i);
            if (s.getName().equals(name)) {
                cvs=s; return s;
            }
            i++;
        }
        return null;
    };
    
    /** select dataset based on its ID (initial dataset has ID 0)/
     * @param i the ID
     * @return selected dataset or <code>null</code> if ID out of range */
    public SVarSet selectSet(final int i) {
        return (i<0||i>=dataset.size())?null:(cvs=(SVarSet)dataset.get(i));
    }
    
    public SVarSet getSet(final int i) {
        return (i<0||i>=dataset.size())?null:(SVarSet)dataset.get(i);
    }

    public int indexOfSet(final SVarSet s) {
	int i=0;
        while (i<dataset.size()) {
            final SVarSet s2=(SVarSet)dataset.get(i);
            if (s2!=null && s2.equals(s)) return i;
	    i++;
        }
        return -1;
    }

    public int getSetIdByName(String name) {
	int i=0;
        while (i<dataset.size()) {
            final SVarSet s=(SVarSet)dataset.get(i);
            if (s.getName().equals(name)) return i;
	    i++;
        }
        return -1;
    }
    
    public int countSets() {
        return dataset.size();
    }
    
    public int curSetId() {
        return dataset.indexOf(cvs);
    }
    
    public String getSetName(final int i) {
        try {
            final SVarSet vs=(SVarSet) dataset.get(i);
            return vs.getName();
        } catch (Exception e) {}
        return null;
    }
    
    public String getSetName() {
        return cvs.getName();
    }

    public boolean removeSetById(final int i) {
	try {
	    dataset.remove(i);
	    return true;
	} catch (Exception e) {};
	return false;
    }
    
    public boolean removeSet(SVarSet s) {
	return dataset.remove(s);
    }

    public void setNoInteractionFlag(boolean flag) {
	noInteraction=flag;
    }

    public boolean getNoInteractionFlag() {
	return noInteraction;
    }

    /** create and select a new dataset with the specified name. please note that it is possible to create
     * multiple datasets of the same name but then only the first of these will be retrieved by name, others
     * have to be selected by ID
     * @param name name of the new dataset
     * @return new dataset */
    public int newSet(String name) {
        cvs=new SVarSet();
        if (name==null) {
            dtctr++;
            name="data."+dtctr;
        }
        cvs.setName(name);
        dataset.add(cvs);
        return dataset.indexOf(cvs);
    }
    
    public int getLength() {
        return (cvs==null || cvs.at(0)==null)?0:cvs.at(0).size();
    }
    
    /** add a variable to the current dataset. Note that many plots assume that all variables of a dataset
     * have the same size.
     * @param v the variable
     * @return index of the variable within the dataset. In order to prevent usage of variables across datasets,
     * most plots take the ID of a variable as parameter and NOT the {@link SVar} object itself.
     */
    public int addVar(final SVar v) {
        if (cvs.getMarker()==null) {
            final SMarker m;
            cvs.setMarker(m=new SMarker(v.size()));
            m.addDepend(this);
        }
        final SMarker m=cvs.getMarker();
        if (v.size()>m.size()) m.resize(v.size());
        return cvs.add(v);
    }
    
    /** shows a variable length mismatch dialog.
	@param name variable causing the mismatch
	@param size of the new variable
	@return -3 for <code>Yex</code> (create a new VarSet), -2 for <code>Cance</code> (don't create the variable), 0 for <code>No</code> (create regardless) */
    int mmDlg(final String name, final int d) {
	if (noInteraction) return -3; /* respond "Yes" if noInteraction is set */
        
        final Frame f=new Frame("dummy");
        f.toFront();
        final MsgDialog md=new MsgDialog(f,"Data length mismatch","Variable \""+name+"\" consists of "+d+" cases, but your current iSet has "+cvs.at(0).size()+" cases.\nDo you want to create a new iSet?",MsgDialog.yesNoCancel);
        int res = 0;
        if ("Cancel".equals(md.lastResult)) res=-2;
        if ("Yes".equals(md.lastResult)) res=-3;
        md.dispose();
        f.dispose();
        return res;
    }
    
    /** shows a modal dialog and waits until it is dismissed.
	@param caption window caption
	@param msg message string
	@param buttons buttons to show (see {@link MsgDialog} constants for default sets of buttons)
	@return text of the button pressed */
    public static String msgDlg(final String caption, final String msg, final String[] buttons) {
	if (noInteraction) { /* if noInteraction is set, try to answer positively */
	    int i=0;
	    while (i<buttons.length) {
		if (buttons[i]!=null && (buttons[i].equals("OK") || buttons[i].equals("Yes")))
		    return buttons[i];
		i++;
	    }
	    return (buttons.length>0)?buttons[0]:"OK";
	}
        final String res;
        final Frame f=new Frame("dummy");
        final MsgDialog md=new MsgDialog(f,caption,msg,buttons);
        res=md.lastResult;
        md.dispose();
        f.dispose();
        return res;
    }
    
    /** get the length of the current dataset */
    public int getCurVarSetLength() {
        return (cvs.count()>0 && cvs.at(0)!=null)?cvs.at(0).size():-1;
    }
    
    /** construct a new numerical variable from supplied array of doubles. Unlike datasets variables cannot have
     * the same name within a dataset.
     * @param name variable name
     * @param d array of doubles
     * @return ID of the new variable, -1 if an error occured (variable name already exists or user declined to create a new iSet), -2 if the user cancelled the operation and -3 if the user opted to create a new iSet
     */
    public int newVar(String name, final double[] d) {
        if (d==null) return -1;
        if (Global.DEBUG>0)
            System.out.println("newVar: double["+d.length+"]");
        if (cvs.count()>0 && cvs.at(0).size()!=d.length) {
	    if (noInteraction) return -3; /* respond "Yes" if noInteraction is set */
            final int i=mmDlg(name,d.length);
            if (i<0) return i;
	    name=getNewTmpVar(name);
        } else {
	    SVar ci = cvs.byName(name);
	    if (ci!=null) {
		if (ci instanceof SVarDouble && ((SVarDouble)ci).hasEqualContents(d)) return cvs.indexOf(name); // re-use
		// not equal, thus we need to re-name it
		name=getNewTmpVar(name);
	    }
	}
	    
        final SVar v=new SVarDouble(name,d);
        return addVar(v);
    }
    
	boolean mapsHaveEqualContents(final SVar v, final double[] x, final double[] y) {
		/* we don't know how to compare them (yet) - FIXME: implement this */
		return false;
	}
	
	/* asserts: x,y!=null, x.length==y.length */
	void loadMapSegments(SVar v, final double[] x, final double[] y) {
		int i = 0, a = 0;
		while (i<x.length) {
			if (Double.isNaN(x[i])) {
				if (i-a-1>0) {
					MapSegment ms = new MapSegment();
					ms.add(x, y, a, i-a-1, false);
					v.add(ms);
				} else v.add(null);
				a=i+1;
			}
			i++;
		}
		if (a<i) {
			MapSegment ms = new MapSegment();
			ms.add(x, y, a, i-a-1, false);
			v.add(ms);
		}
	}
	
	/** construct a new map variable - SVar consisting of MapSegment instances */
	public int newVar(String name, final double[] x, final double[] y) {
		if (x==null || y==null || x.length!=y.length) return -1;
        if (Global.DEBUG>0)
            System.out.println("newVar: polygons["+x.length+"]");
        /* FIXME! we don't know the length of the variable yet, so we just carry on ... */
		/*
		if (cvs.count()>0 && cvs.at(0).size()!=x.length) {
			if (noInteraction) return -3; // respond "Yes" if noInteraction is set
            final int i=mmDlg(name,d.length);
            if (i<0) return i;
			name=getNewTmpVar(name);
        } else */ {
			SVar ci = cvs.byName(name);
			if (ci!=null) {
				if (ci.getContentsType() == SVar.CT_Map && mapsHaveEqualContents(ci, x, y)) return cvs.indexOf(name); // re-use
																										  // not equal, thus we need to re-name it
				name=getNewTmpVar(name);
			}
		}
		SVar v=new SVarObj(name, false, false);
		v.setContentsType(SVar.CT_Map);
		loadMapSegments(v, x, y);
        return addVar(v);
	}
	
    /** construct a new numerical variable from supplied array of integers. Unlike datasets variables cannot have
     * the same name within a dataset.
     * @param name variable name
     * @param d array of integers
     * @return ID of the new variable or -1 if error occured (variable name already exists etc.)
     */
    public int newVar(String name, final int[] d) {
        if (d==null) return -1;
        if (Global.DEBUG>0)
            System.out.println("newVar: int["+d.length+"]");
        if (cvs.count()>0 && cvs.at(0).size()!=d.length) {
	    if (noInteraction) return -3; /* respond "Yes" if noInteraction is set */
            final int i=mmDlg(name,d.length);
            if (i<0) return i;
	    name=getNewTmpVar(name);
        } else {
	    SVar ci = cvs.byName(name);
	    if (ci!=null) {
		if (ci instanceof SVarInt && ((SVarInt)ci).hasEqualContents(d)) return cvs.indexOf(name); // re-use
		// not equal, thus we need to re-name it
		name=getNewTmpVar(name);
	    }
	}
        final SVar v=new SVarInt(name, d);
        return addVar(v);
    }
    
    /** construct a new categorical variable from supplied array of strings. Unlike datasets variables cannot have
     * the same name within a dataset.
     * @param name variable name
     * @param d array of strings
     * @return ID of the new variable or -1 if error occured (variable name already exists etc.)
     */
    public int newVar(final String name, final String[] d) {
        if (d==null) return -1;
        if (Global.DEBUG>0)
            System.out.println("newVar: String[]");
        if (cvs.count()>0 && cvs.at(0).size()!=d.length) {
	    if (noInteraction) return -3; /* respond "Yes" if noInteraction is set */
            final int i=mmDlg(name,d.length);
            if (i<0) return i;
        }
        final SVar v=new SVarObj(name);
        int i=0; while(i<d.length) v.add(d[i++]);
        return addVar(v);
    }
    
    /** construct a new factor variable from supplied array of integers (cases) and strings (levels). Unlike datasets variables cannot have the same name within a dataset.
     * @param name variable name
     * @param ix array of level IDs. IDs out of range (<1 or >length(d)) are treated as missing values
     * @param d levels (d[0]=ID 1, d[1]=ID 2, ...)
     * @return ID of the new variable or -1 if error occured (variable name already exists etc.)
     */
    public int newVar(String name, final int[] ix, final String[] d) {
        if (ix==null) return -1;
        if (d==null) return newVar(name,ix);
        if (Global.DEBUG>0)
            System.out.println("newVar: int["+ix.length+"] + levels["+d.length+"]");
        int j=0;
        while (j<ix.length) { ix[j++]--; } // reduce index by 1 since R is 1-based
        if (cvs.count()>0 && cvs.at(0).size()!=ix.length) {
	    if (noInteraction) return -3; /* respond "Yes" if noInteraction is set */
            final int i=mmDlg(name,ix.length);
            if (i<0) return i;
	    name=getNewTmpVar(name);
        } else {
	    SVar ci = cvs.byName(name);
	    if (ci!=null) {
		if (ci instanceof SVarFact && ((SVarFact)ci).hasEqualContents(ix,d)) return cvs.indexOf(name); // re-use
		// not equal, thus we need to re-name it
		name=getNewTmpVar(name);
	    }
	}
        final SVar v=new SVarFact(name, ix, d);
        return addVar(v);
    }
    
    public int newVar(final String name, final int[] ix, final String d) {
        return newVar(name,ix, new String[]{d});
    }
    
    public static String[] toStringArray(final Object[] o) {
        final String[] s=new String[o.length];
        int i=0; while(i<o.length) { if (o[i]!=null) s[i]=o[i].toString(); i++; }
        return s;
    }
    
    /** replaces the content of a variable. it is meant for modification ONLY. note that the length of
     * the new content cannot exceed the original size of the variable, no cases are added.
     * @param vi ID of the variable
     * @param d new content
     * @return variable ID (same as vi) */
    public int replaceVar(final int vi, final double[] d) {
        final SVar v=cvs.at(vi);
        if (v==null) return -1;
	if (v instanceof SVarDouble) {
	    ((SVarDouble)v).replaceAll(d);
	} else {
	    /*
	    v.getNotifier().beginBatch();
	    int i=0; while(i<d.length) { v.replace(i,new Double(d[i])); i++; }
	    v.getNotifier().endBatch(); */
	    return -1;
	}
        return vi;
    }
    
    /** replaces the content of a variable. it is meant for modification ONLY. note that the length of
     * the new content cannot exceed the original size of the variable, no cases are added.
     * @param vi ID of the variable
     * @param d new content
     * @return variable ID (same as vi) */
    public int replaceVar(final int vi, final int[] d) {
        final SVar v=cvs.at(vi);
        if (v==null) return -1;
	if (v instanceof SVarInt) {
	    ((SVarInt)v).replaceAll(d);
	} else	if (v instanceof SVarDouble) {
	    ((SVarDouble)v).replaceAll(d);
	} else {
	    /*
	      v.getNotifier().beginBatch();
	      int i=0; while(i<d.length) { v.replace(i,new Integer(d[i])); i++; }
	      v.getNotifier().endBatch();
	    */
	    return -1;
	}
        return vi;
    }
    
    /** replaces the content of a variable. it is meant for modification ONLY. note that the length of
     * the new content cannot exceed the original size of the variable, no cases are added.
     * @param vi ID of the variable
     * @param d new content
     * @return variable ID (same as vi) */
    public int replaceVar(final int vi, final int[] ids, final String[] levels) {
        final SVar v=cvs.at(vi);
        if (v==null) return -1;
	if (v instanceof SVarFact) {
	    ((SVarFact)v).replaceAll(ids, levels);
	} else {
	/*
	  v.getNotifier().beginBatch();
	  int i=0; while(i<d.length) { v.replace(i,new Integer(d[i])); i++; }
	  v.getNotifier().endBatch(); */
	    return -1;
	}
        return vi;
    }
    
    /** updates any plots associated with the current dataset by sending NM_VarContentChange message */
    public void update() {
        final SMarker m=cvs.getMarker();
        if (m!=null) m.NotifyAll(new NotifyMsg(m,Common.NM_VarContentChange));
    }
    
    /** get variable object associated with an ID in current dataset
     * @param i variable ID
     * @return variable object or <code>null</code> if ID is invalid */
    public SVar getVar(final int i) { return cvs.at(i); };
    
    /** get first variable object associated with a name in current dataset
     * @param i variable name
     * @return variable object or <code>null</code> if var of that name doesn't exist */
    public SVar getVar(final String name) { return cvs.byName(name); };
    
    /** beware!! this updateMerker has nothing to do with updateMarker() !!!!! bad thing! */
    public void updateMarker(final SVarSet vs, final int vid) {
        if (vs.getMarker()==null) {
            final SMarker m=new SMarker(vs.at(vid).size());
            vs.setMarker(m);
            m.addDepend(this);
        }
    }
    
    public FrameDevice newFrame() {
    	return newFrame("<unnamed>",true,0);
    }
    
    public FrameDevice newFrame(String tit) {
    	return newFrame(tit,true,FrameDevice.clsCustom);
    }
    
    public FrameDevice newFrame(String tit, int wclass) {
    	return newFrame(tit,true,wclass);
    }
    
    public FrameDevice newFrame(String tit, boolean useCommonBg, int wclass) {
    	if(graphicsEngine==PlotComponent.AWTGrDevID) return new TFrame(tit,useCommonBg,wclass);
    	else if(graphicsEngine==PlotComponent.SWINGGrDevID) return new TJFrame(tit,useCommonBg,wclass);
    	else if(graphicsEngine==PlotComponent.JOGLGrDevID) return new TFrame(tit,useCommonBg,wclass);
    	else return new TFrame(tit,useCommonBg,wclass);
    }
    
    public void setGraphicsEngine(final int greng) {
        graphicsEngine = greng;
    }
	
	public int getGraphicsEngine() {
		return graphicsEngine;
	}
    
    public void addNewPlot(BaseCanvas bc) {
        if(plots==null) plots=new BaseCanvas[1];
        else { BaseCanvas[] temp=plots;
        	   plots = new BaseCanvas[temp.length+1];
        	   System.arraycopy(temp,0,plots,0,temp.length);
        	   temp=null; }
        plots[plots.length-1]=bc;
    }
    
    /** display a new scatterplot of two variables from current dataset
     * @param v1 X-axis variable
     * @param v2 Y-axis variable
     * @return scatterplot canvas object */
    public ScatterCanvas newScatterplot(final int v1, final int v2) { return newScatterplot(cvs,v1,v2); }
    public ScatterCanvas newScatterplot(final SVarSet vs, final int v1, final int v2) {
        updateMarker(vs,v1);
        
	String title="Scatterplot ("+vs.at(v2).getName()+" vs "+vs.at(v1).getName()+")";
        FrameDevice frdev;
        frdev=newFrame(title,TFrame.clsScatter);
        frdev.initPlacement();
        frdev.setVisible(true);
        frdev.addWindowListener(Common.getDefaultWindowListener());
        final ScatterCanvas sc=new ScatterCanvas(graphicsEngine,frdev.getFrame(),vs.at(v1),vs.at(v2),vs.getMarker());
		frdev.add(sc.getComponent());
        if (vs.getMarker()!=null) vs.getMarker().addDepend(sc);
        sc.setSize(new Dimension(400,300));
	sc.setTitle(title);
        frdev.setSize(new Dimension(sc.getWidth(),sc.getHeight()));
        frdev.pack();
        sc.repaint();

        addNewPlot(sc);
        return sc;
    }
    
	public MapCanvas newMap(final int v) { return newMap(cvs, v); }
	public MapCanvas newMap(final SVarSet vs, final int v) {
		updateMarker(vs, v);
		final SVar segs = vs.at(v);
		if (segs == null || segs.getContentsType()!=SVar.CT_Map) return null;
		String title = "Map ("+segs.getName()+")";

		FrameDevice frdev = newFrame(title,TFrame.clsMap);
        frdev.initPlacement();
        frdev.setVisible(true);
        frdev.addWindowListener(Common.getDefaultWindowListener());
        final MapCanvas bc=new MapCanvas(graphicsEngine, frdev.getFrame(), segs, vs.getMarker());
        frdev.add(bc.getComponent());
        if (vs.getMarker()!=null) vs.getMarker().addDepend(bc);
        bc.setSize(new Dimension(400,300));
		bc.setTitle(title);
        frdev.setSize(new Dimension(bc.getWidth(),bc.getHeight()));
        frdev.pack();
        bc.repaint();
		
        addNewPlot(bc);
        return bc;
	}
	
    public BarCanvas newBarchart(final int v) { return newBarchart(cvs,v,-1); }
    public BarCanvas newBarchart(final int v, final int wgt) { return newBarchart(cvs,v,wgt); }
    public BarCanvas newBarchart(final SVarSet vs, final int v, final int wgt) {
        updateMarker(vs,v);
        final SVar theCat=vs.at(v);
        final SVar theNum=(wgt<0)?null:vs.at(wgt);
        if (theCat==null) return null;
        if (!theCat.isCat()) theCat.categorize();
        
        FrameDevice frdev;
	String title=(theNum!=null)?"w.Barchart ("+theCat.getName()+"*"+theNum.getName()+")":"Barchart ("+theCat.getName()+")";
        frdev = newFrame(title,TFrame.clsBar);
        frdev.initPlacement();
        frdev.setVisible(true);
        frdev.addWindowListener(Common.getDefaultWindowListener());
        final BarCanvas bc=new BarCanvas(graphicsEngine,frdev.getFrame(),theCat,vs.getMarker(),theNum);
        frdev.add(bc.getComponent());
        if (vs.getMarker()!=null) vs.getMarker().addDepend(bc);
        int xdim=100+40*theCat.getNumCats();
        if (xdim>800) xdim=800;
        bc.setSize(new Dimension(xdim,200));
	bc.setTitle(title);
        frdev.setSize(new Dimension(bc.getWidth(),bc.getHeight()));
        frdev.pack();
        bc.repaint();

        addNewPlot(bc);
        return bc;
    }
    
    public LineCanvas newLineplot(final int[] v) { return newLineplot(cvs,-1,v); }
    public LineCanvas newLineplot(final int rv, final int[] v) { return newLineplot(cvs,rv,v); }
    public LineCanvas newLineplot(final int rv, final int v) { final int vv[]=new int[1]; vv[0]=v; return newLineplot(cvs,rv,vv); }
    public LineCanvas newLineplot(final SVarSet vs, final int rv, final int[] v) {
        if (v.length==0) return null;
        updateMarker(vs,v[0]);
        
        FrameDevice frdev;
	String title="Lineplot";
        frdev = newFrame(title,TFrame.clsLine);
        frdev.initPlacement();
        frdev.setVisible(true);
        frdev.addWindowListener(Common.getDefaultWindowListener());
        final SVar[] vl=new SVar[v.length];
        int i=0;
        while(i<v.length) { vl[i]=vs.at(v[i]); i++; }
        final LineCanvas lc=new LineCanvas(graphicsEngine,frdev.getFrame(),vs.at(rv),vl,vs.getMarker());
        frdev.add(lc.getComponent());
        if (vs.getMarker()!=null) vs.getMarker().addDepend(lc);
        lc.setSize(new Dimension(400,300));
	lc.setTitle(title);
        frdev.setSize(new Dimension(lc.getWidth(),lc.getHeight()));
        frdev.pack();
        lc.repaint();

// LineCanvas is not a BaseCanvas
//        addNewPlot(lc);
        return lc;
    };
    
    public HamCanvas newHammock(final int[] v) { return newHammock(cvs,v); }
    public HamCanvas newHammock(final SVarSet vs, final int[] v) {
        if (v.length==0) return null;
        updateMarker(vs,v[0]);
        
        FrameDevice frdev;
	String title="Hammock plot";
        frdev = newFrame(title,TFrame.clsPCP);
        frdev.initPlacement();
        frdev.setVisible(true);
        frdev.addWindowListener(Common.getDefaultWindowListener());
        final SVar[] vl=new SVar[v.length];
        int i=0;
        while(i<v.length) { vl[i]=vs.at(v[i]); i++; }
        final HamCanvas hc=new HamCanvas(graphicsEngine,frdev.getFrame(),vl,vs.getMarker());
        frdev.add(hc.getComponent());
        if (vs.getMarker()!=null) vs.getMarker().addDepend(hc);
        hc.setSize(new Dimension(400,300));
	hc.setTitle(title);
        frdev.setSize(new Dimension(hc.getWidth(),hc.getHeight()));
        frdev.pack();
        hc.repaint();
        
        addNewPlot(hc);
        return hc;
    }
    
    
    public MosaicCanvas newMosaic(final int[] v) { return newMosaic(cvs,v); }
    public MosaicCanvas newMosaic(final SVarSet vs, final int[] v) {
        if (v.length==0) return null;
        updateMarker(vs,v[0]);
        String title = "(";
        for (int i = 0; i < v.length-1; i++)
            title += vs.at(v[i]).getName()+", ";
        title += vs.at(v[v.length-1]).getName()+")";
        
        FrameDevice frdev;
        frdev = newFrame("Mosaic plot "+title,TFrame.clsPCP);
    	frdev.initPlacement();
        frdev.setVisible(true);
        frdev.addWindowListener(Common.getDefaultWindowListener());
        final SVar[] vl=new SVar[v.length];
        int i=0;
        while(i<v.length) { vl[i]=vs.at(v[i]); i++; }
        final MosaicCanvas mc=new MosaicCanvas(graphicsEngine,frdev.getFrame(),vl,vs.getMarker());
        frdev.add(mc.getComponent());
        if (vs.getMarker()!=null) vs.getMarker().addDepend(mc);
        mc.setSize(new Dimension(400,300));
	mc.setTitle("Mosaic plot "+title);
        frdev.setSize(new Dimension(mc.getWidth(),mc.getHeight()));
        frdev.pack();
        mc.repaint();
        
        addNewPlot(mc);
        return mc;
    }
    
    
    public ParallelAxesCanvas newPCP(final int[] v) { return newPCP(cvs,v); }
    public ParallelAxesCanvas newPCP(final SVarSet vs, final int[] v) {
        if (v.length==0) return null;
        updateMarker(vs,v[0]);
        
        FrameDevice frdev;
	String title="Parallel coord. plot ("+vs.getName()+")";
        frdev = newFrame(title,TFrame.clsPCP);
	frdev.initPlacement();
        frdev.setVisible(true);
        frdev.addWindowListener(Common.getDefaultWindowListener());
        final SVar[] vl=new SVar[v.length];
        int i=0;
        while(i<v.length) { vl[i]=vs.at(v[i]); i++; }
        final ParallelAxesCanvas pcpc=new ParallelAxesCanvas(graphicsEngine,frdev.getFrame(),vl,vs.getMarker(),ParallelAxesCanvas.TYPE_PCP);
        frdev.add(pcpc.getComponent());
        if (vs.getMarker()!=null) vs.getMarker().addDepend(pcpc);
        pcpc.setSize(new Dimension(400,300));
	pcpc.setTitle(title);
        frdev.setSize(new Dimension(pcpc.getWidth(),pcpc.getHeight()));
        frdev.pack();
        pcpc.repaint();
        
        addNewPlot(pcpc);
        return pcpc;
    }
    
    /** display a new histogram of a variables from current dataset
     * @param v variable ID
     * @return histogram canvas object */
    public HistCanvas newHistogram(final int v) { return newHistogram(cvs,v); };
    public HistCanvas newHistogram(final SVarSet vs, final int i) {
        updateMarker(vs,i);

        FrameDevice frdev;
	String title="Histogram ("+vs.at(i).getName()+")";
        frdev = newFrame(title, TFrame.clsHist);
        frdev.initPlacement();
        frdev.setVisible(true);
        frdev.addWindowListener(Common.getDefaultWindowListener());
        final HistCanvas hc=new HistCanvas(graphicsEngine,frdev.getFrame(),vs.at(i),vs.getMarker());
        frdev.add(hc.getComponent());
        hc.updateObjects();
        if (vs.getMarker()!=null) vs.getMarker().addDepend(hc);
        hc.setSize(new Dimension(400,300));
	hc.setTitle(title);
        frdev.setSize(new Dimension(hc.getWidth(), hc.getHeight()));
        frdev.pack();
        hc.repaint();
        
        addNewPlot(hc);
        return hc;
    };
    
    public ParallelAxesCanvas newBoxplot(final int i) { return newBoxplot(cvs,new int[]{i},-1); }
    public ParallelAxesCanvas newBoxplot(final int i, final int ic) { return newBoxplot(cvs,new int[]{i},ic); }
    public ParallelAxesCanvas newBoxplot(final int[] i) { return newBoxplot(cvs,i,-1); }
    public ParallelAxesCanvas newBoxplot(final int[] i, final int ic) { return newBoxplot(cvs,i,ic); }
    public ParallelAxesCanvas newBoxplot(final SVarSet vs, final int i[], final int ic) {
        final SVar catVar=(ic<0)?null:vs.at(ic);
        updateMarker(vs,i[0]);
        
        FrameDevice frdev;
	String title="Boxplot ("+vs.at(i[0]).getName()+")"+((catVar!=null)?" by "+catVar.getName():"");
        frdev = newFrame(title,TFrame.clsBox);
        frdev.initPlacement();
        frdev.setVisible(true);
        frdev.addWindowListener(Common.getDefaultWindowListener());
        final SVar[] vl=new SVar[i.length];
        int j=0;
        while(j<i.length) { vl[j]=vs.at(i[j]); j++; }
        final ParallelAxesCanvas bc=(catVar==null)?new ParallelAxesCanvas(graphicsEngine,frdev.getFrame(),vl,vs.getMarker(),ParallelAxesCanvas.TYPE_BOX):new ParallelAxesCanvas(graphicsEngine,frdev.getFrame(),vl[0],catVar,vs.getMarker(),ParallelAxesCanvas.TYPE_BOX);
        frdev.add(bc.getComponent());
        if (vs.getMarker()!=null) vs.getMarker().addDepend(bc);
        int xdim=(catVar==null)?(40+40*i.length):(40+40*catVar.getNumCats());
        if (xdim>800) xdim=800;
        bc.setSize(new Dimension(xdim,200));
	bc.setTitle(title);
        frdev.setSize(new Dimension(bc.getWidth(),bc.getHeight()));
        frdev.pack();
        bc.repaint();
        
        addNewPlot(bc);
        return bc;
    };
    
    // no VarFRame since that's Klimt-specific
    /** display a new variables frame
     * @return variable frame object
     * public VarFrame newVarFrame() { return newVarFrame(cvs); };
     * public VarFrame newVarFrame(SVarSet v) {
     * VarFrame vf=new VarFrame(v,10,10,150,400);
     * return vf;
     * };
     */
    public double[] getDoubleContent(final int vid) {
        final SVar v=cvs.at(vid);
        if (v==null) return null;
        final double[] d=new double[v.size()];
        int i=0;
        while(i<v.size()) { d[i]=v.atD(i); i++; }
        return d;
    };
    
    public String[] getStringContent(final int vid) {
        final SVar v=cvs.at(vid);
        if (v==null) return null;
        final String[] d=new String[v.size()];
        int i=0;
        while(i<v.size()) { d[i]=v.atS(i); i++; }
        return d;
    };
    
    public double[] getDoubleContent(final SVar v) {
        if (v==null) return null;
        final double[] d=new double[v.size()];
        int i=0;
        while(i<v.size()) { d[i]=v.atD(i); i++; }
        return d;
    }
    
    public String[] getStringContent(final SVar v) {
        if (v==null) return null;
        final String[] d=new String[v.size()];
        int i=0;
        while(i<v.size()) { d[i]=v.atS(i); i++; }
        return d;
    }
    
    public int varIsNum(final int vid) {
        final SVar v=cvs.at(vid);
        if (v==null) return -1;
        return (v.isNum())?1:0;
    }
    
    public int varIsNum(final SVar v) {
        if (v==null) return -1;
        return (v.isNum())?1:0;
    }
    
    public void setSecMark(final int[] ml) { setSecMark(ml,true); }
    public void setSecMark(final int[] ml, final boolean circular) {
        if (cvs==null) return;
        int i=0, j=0;
        final SMarker m=cvs.getMarker();
        while(i<ml.length && j<m.size()) {
            m.setSec(j,ml[i]);
            i++; j++;
            if (circular && i>=ml.length) i=0;
        }
        m.NotifyAll(new NotifyMsg(this,Common.NM_SecMarkerChange));
    }
    
    public void updateMarker() {
        if (cvs!=null) cvs.getMarker().NotifyAll(new NotifyMsg(this,Common.NM_MarkerChange));
    }
    
    public void updateVars() {
        if (cvs!=null) cvs.getMarker().NotifyAll(new NotifyMsg(this,Common.NM_VarChange));
    }
    
    public void actionPerformed(final ActionEvent e) {
        Common.breakDispatcher.NotifyAll(new NotifyMsg(this,Common.NM_ActionEvent,e.getActionCommand()));
    }
    
    public void setDebugLevel(final int df) {
        if (Global.DEBUG>0) System.out.println("Setting DEBUG level to "+df);
        Global.DEBUG=df;
        if (Global.DEBUG>0) System.out.println("DEBUG level set to "+Global.DEBUG);
    }
    
    //=============================== EVENT LOOP STUFF =========================================
    
    private boolean notificationArrived=false;
    private NotifyMsg lastNotificationMessage; // only synchronized methods are allowed to use this
    
    /** this internal method waits until {@link #triggerNotification} is called by another thread. It is implemented by using {@link wait()} and checking {@link notificationArrived}. */
    private synchronized NotifyMsg waitForNotification() {
        while (!notificationArrived) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
        notificationArrived=false;
        final NotifyMsg m=lastNotificationMessage;
        lastNotificationMessage=null; // reset lastNM
        return m;
    }
    
    /** this methods awakens {@link #waitForNotification}. It is implemented by setting {@link #notificationArrived} to <code>true</code>, setting {@link #lastNotificationMessage} to the passed message and finally calling {@link notifyAll()}. */
    private synchronized void triggerNotification(final NotifyMsg msg) {
        notificationArrived=true;
        lastNotificationMessage=msg;
        notifyAll();
    }
    
    /** is a message arrives we'll simply use {@link #triggerNotification} to inform any sleeping calls to {@link #waitForNotification} */
    public void Notifying(final NotifyMsg msg, final Object o, final Vector path) {
        triggerNotification(msg);
    }
    
    /** this methods is called from R by ievent.wait and uses {@link #waitForNotification} to wait for an event. */
    public NotifyMsg eventWait() {
        final NotifyMsg m=waitForNotification();
        return (m==null || m.getMessageID()==Common.NM_BREAK)?null:m;
    }
    
    public String d2s(final double d) {
        return Double.toString(d);
    }
    
    // i think for R only these methods are needed
    // plotID==-1 -> all plots get the same String
    public void setExtQueryString(int plotID, String str) {
    	if(str==null) {useExtQueryString(plotID,false);return;}
    	if(plots==null || plotID<-1 || plotID>plots.length) return; // plotIDs begin with 1
    	if(plotID==-1) {
    		for(int i=0;i<plots.length;i++) {
    			plots[i].setExtQueryString(str);
    		}
    	}
    	else plots[plotID-1].setExtQueryString(str);
    }
    
    public void useExtQueryString(int plotID, boolean b) {
    	if(plots==null || plotID<-1 || plotID>plots.length) return; // plotIDs begin with 1
    	if(plotID==-1) {
    		for(int i=0;i<plots.length;i++) {
    			plots[i].useExtQuery(b);
    		}
    	}
    	else plots[plotID-1].useExtQuery(b);
    }
    
}
