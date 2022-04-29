package org.rosuda.ibase.plots;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.PrintStream;

import org.rosuda.ibase.*;
import org.rosuda.ibase.toolkit.*;
import org.rosuda.pograss.*;
import org.rosuda.util.*;

/** implementation of line plot
    @version $Id: LineCanvas.java 2244 2006-04-18 09:10:27Z goubermana $
*/
public class LineCanvas extends PGSCanvas implements Dependent, MouseListener, MouseMotionListener, KeyListener, ActionListener, Commander
{
    public static final int LT_DIRECT = 0;
    public static final int LT_RECT   = 1;

    /** line type */
    int type=LT_DIRECT;
    
    /** variables; 0=x, 1,2,3...=Y */
    SVar v[];
    /** associated marker */
    SMarker m;

    /** flag whether axis labels should be shown */
    boolean showLabels=true;

    /** flag whether jittering should be used in case X is categorical */
    boolean jitter=false;

    /** flag whether alternative selection style should be used */
    boolean selRed=false;

    /** falg to allow lines to be drawn from right to left */
    boolean drawBackline=false;
        
    /** array of axes */
    Axis A[];

    int x1, y1, x2, y2;
    boolean drag;

    public boolean ignoreMarker=false;

    MenuItem MIlabels=null;

    int X,Y,W,H, TW,TH;
    double totMin, totMax;
    /** create a new lineplot
	@param f associated frame (or <code>null</code> if none)
	@param v1 variable 1
	@param v2 variable 2
	@param mark associated marker */
    public LineCanvas(final int gd, Frame f, SVar xv, SVar[] yvs, SMarker mark) {
        super(gd,3); // 3 layers; 0=base+points, 1=selected, 2=drag
		setFrame(f);
		v=new SVar[yvs.length+1];
		A=new Axis[2];
        m=mark; if (m!=null) m.addDepend(this);
	int i=0;
        String vnlist=null;
	while(i<yvs.length) {
            //System.out.println(yvs[i].getName()+": min="+yvs[i].getMin()+", max="+yvs[i].getMax());
	    if (i==0) {
		totMin=yvs[i].getMin(); totMax=yvs[i].getMax();
	    } else {
		if (yvs[i].getMin()<totMin) totMin=yvs[i].getMin();
		if (yvs[i].getMax()>totMax) totMax=yvs[i].getMax();
	    };
            if (vnlist==null) vnlist=yvs[i].getName(); else vnlist+=","+yvs[i].getName();
	    v[i+1]=yvs[i]; i++;
	};
        //System.out.println("Total: min="+totMin+", max="+totMax);
	A[1]=new Axis(null,Axis.O_Y,Axis.T_Num);
	A[1].setValueRange(totMin,totMax-totMin);
         A[1].addDepend(this);
	if (xv==null) {
	    xv=new SVarObj("index.LC");
	    i=1; while(i<=v[1].size()) { xv.add(new Integer(i)); i++; };
            setTitle("Series plot of "+vnlist);
        } else setTitle("Series plot of "+xv.getName()+" vs "+vnlist);
        f.setTitle(getTitle());
	v[0]=xv; A[0]=new Axis(v[0],Axis.O_X,v[0].isCat()?Axis.T_EqCat:Axis.T_Num); A[0].addDepend(this);
	A[0].setValueRange(v[0].getMin()-(v[0].getMax()-v[0].getMin())/20,(v[0].getMax()-v[0].getMin())*1.1);

	setBackground(Common.backgroundColor);
	drag=false;
	addMouseListener(this);
	addMouseMotionListener(this);
	addKeyListener(this); f.addKeyListener(this);
	String myMenu[]={"+","File","~File.Graph","~Edit","+","View","@RRotate","rotate","@LHide labels","labels","!HToggle hilight. style","selRed","@JToggle jittering","jitter","@BToggle back-lines","backlines","-","Set X Range ...","XrangeDlg","Set Y Range ...","YrangeDlg","~Window","0"};
	EzMenu.getEzMenu(f,this,myMenu);
	MIlabels=EzMenu.getItem(f,"labels");
        MenuItem mi=EzMenu.getItem(f,"rotate");
        if (mi!=null) mi.setEnabled(false);
    };

    public LineCanvas(Frame f, SVar xv, SVar[] yvs, SMarker mark) {
		this(0, f, xv, yvs, mark);
	}

    public Axis getXAxis() { return A[0]; }
    public Axis getYAxis() { return A[1]; }
    
    public Dimension getMinimumSize() { return new Dimension(60,50); };

    public void rotate() {
	/*
	SVar h=v[0]; v[0]=v[1]; v[1]=h;
	Axis ha=A[0]; A[0]=A[1]; A[1]=ha;
	try {
	    ((Frame) getParent()).setTitle("Scatterplot ("+v[1].getName()+" vs "+v[0].getName()+")");
	} catch (Exception ee) {};
        setUpdateRoot(0);
	repaint();
	*/
    };

    public void setLineType(int nt) {
        type=nt; setUpdateRoot(0); repaint();
    }
    
    public void Notifying(NotifyMsg msg, Object o, Vector path) {
        setUpdateRoot((msg.getMessageID()==Common.NM_MarkerChange)?0:0);
        repaint();
    };

    public void paintPoGraSS(PoGraSS g) {
	Rectangle r=getBounds();
	g.setBounds(r.width,r.height);
	g.begin();
	g.defineColor("white",255,255,255);
	if (selRed)
	    g.defineColor("marked",255,0,0);
	else
	    g.defineColor("marked",128,255,128);
	g.defineColor("black",0,0,0);
	g.defineColor("outline",0,0,0);
	g.defineColor("point",0,0,128);	
	g.defineColor("red",255,0,0);
	g.defineColor("line",0,0,128); // color of line plot
	g.defineColor("lines",96,96,255);	
	g.defineColor("selText",255,0,0);
	g.defineColor("selBg",255,255,192);
	g.defineColor("splitRects",128,128,255);

	Dimension Dsize=getSize();
	if (Dsize.width!=TW || Dsize.height!=TH) {
	    int w=Dsize.width, h=Dsize.height;
	    TW=w; TH=h;
	    int innerL=45, innerB=30, lshift=0;
	    int innerW=w-innerL-10, innerH=h-innerB-10;
	
	    A[0].setGeometry(Axis.O_X,X=innerL,W=innerW);
	    A[1].setGeometry(Axis.O_Y,innerB,H=innerH);
	    Y=TH-innerB-innerH;
	};

	if (TW<50||TH<50) {
	    g.setColor("red");
	    g.drawLine(0,0,TW,TH); 
	    g.drawLine(0,TH,TW,0); 
	    return;
	};

	g.setColor("white");
	g.fillRect(X,Y,W,H);
        g.setColor("black");
        g.drawLine(X,Y,X,Y+H);
        g.drawLine(X,Y+H,X+W,Y+H);

	/* draw ticks and labels for X axis */
        {
            double f=A[0].getSensibleTickDistance(50,26);
            double fi=A[0].getSensibleTickStart(f);
            //if (Common.DEBUG>0)
            //System.out.println("SP.A[0]:"+A[0].toString()+", distance="+f+", start="+fi);
            while (fi<A[0].vBegin+A[0].vLen) {
                int t=A[0].getValuePos(fi);
                g.drawLine(t,Y+H,t,Y+H+5);
                if (showLabels)
                    g.drawString(v[0].isCat()?v[0].getCatAt((int)fi).toString():
                                 A[0].getDisplayableValue(fi),t-5,Y+H+20);
                fi+=f;
            };
        }

	/* draw ticks and labels for Y axis */
        {
            double f=A[1].getSensibleTickDistance(50,18);
            double fi=A[1].getSensibleTickStart(f);
            //if (Common.DEBUG>0)
            //System.out.println("SP.A[1]:"+A[1].toString()+", distance="+f+", start="+fi);
            while (fi<A[1].vBegin+A[1].vLen) {
                int t=TH-A[1].getValuePos(fi);
                g.drawLine(X-5,t,X,t);
                if(showLabels)
                    g.drawString(v[1].isCat()?Common.getTriGraph(v[1].getCatAt((int)fi).toString()):A[1].getDisplayableValue(fi),X-25,t+5);
                fi+=f;
            };
        }

	g.setColor("line");
	for (int j=1;j<v.length;j++) {
            if (j==2) g.setColor(255,0,0);
            if (j==3) g.setColor(0,0,255);
            if (j==4) g.setColor(128,0,128);
            if (j==5) g.setColor(0,128,128);
            if (j==6) g.setColor(128,128,0);
            if (j==7) g.setColor(0,0,0);
            boolean lmc=false;
            for (int i=1;i<v[0].size() && i<v[j].size();i++) {
                int x1=A[0].getCasePos(i-1), x2=A[0].getCasePos(i);
                if ((drawBackline || x2>=x1) && v[j].at(i)!=null && v[j].at(i-1)!=null) {
                    if (m.at(i)!=lmc) { lmc=!lmc;
                        if (lmc && !ignoreMarker)
                            g.setColor("marked");
                        else {
                            if (j==1) g.setColor("line");
                            if (j==2) g.setColor(255,0,0);
                            if (j==3) g.setColor(0,0,255);
                            if (j==4) g.setColor(128,0,128);
                            if (j==5) g.setColor(0,128,128);
                            if (j==6) g.setColor(128,128,0);
                            if (j>6) g.setColor(0,0,0);
                        }
                    }
                    if (type==LT_DIRECT) {
                        g.drawLine(A[0].getCasePos(i-1),TH-A[1].getValuePos(v[j].atD(i-1)),
                                   A[0].getCasePos(i),TH-A[1].getValuePos(v[j].atD(i)));
                    } else {
                        g.drawLine(A[0].getCasePos(i-1),TH-A[1].getValuePos(v[j].atD(i-1)),
                                   A[0].getCasePos(i),TH-A[1].getValuePos(v[j].atD(i-1)));
                        g.drawLine(A[0].getCasePos(i),TH-A[1].getValuePos(v[j].atD(i-1)),
                                   A[0].getCasePos(i),TH-A[1].getValuePos(v[j].atD(i)));
                    };
                }
            }
	};
	
        nextLayer(g);
        
	/*
        if (m.marked()>0) {
            g.setColor("marked");
            for (int i=0;i<m.size();i++)
                if (m.at(i))
                    if (selRed)
                        g.fillOval(Pts[i].x-2,Pts[i].y-2,4,4);
                    else
                        g.fillOval(Pts[i].x-1,Pts[i].y-1,3,3);
			};*/
                
        if (drag) {
            nextLayer(g);
	    int dx1=A[0].clip(x1),dy1=TH-A[1].clip(TH-y1),
		dx2=A[0].clip(x2),dy2=TH-A[1].clip(TH-y2);
	    if (dx1>dx2) { int h=dx1; dx1=dx2; dx2=h; };
	    if (dy1>dy2) { int h=dy1; dy1=dy2; dy2=h; };
	    g.setColor("black");
	    g.drawRect(dx1,dy1,dx2-dx1,dy2-dy1);
	};

	g.end();
        setUpdateRoot(3); // by default no repaint is necessary unless resize occurs
    };

    public void mouseClicked(MouseEvent ev) 
    {
	int x=ev.getX(), y=ev.getY();
	x1=x-2; y1=y-2; x2=x+3; y2=y+3; drag=true; mouseReleased(ev);
    };

    public void mousePressed(MouseEvent ev) 
    {	
	x1=ev.getX(); y1=ev.getY();
	drag=true;
    };
    public void mouseReleased(MouseEvent e)
    {
	int X1=x1, Y1=y1, X2=x2, Y2=y2;
	if (x1>x2) { X2=x1; X1=x2; };
	if (y1>y2) { Y2=y1; Y1=y2; };
	Rectangle sel=new Rectangle(X1,Y1,X2-X1,Y2-Y1);

	int setTo=0;
	if (e.isControlDown()) setTo=1;
	if (!e.isShiftDown()) m.selectNone();
	
	drag=false; 
	/*
	int i=0;
	while (i<pts) {
	    if (Pts[i]!=null && sel.contains(Pts[i]))
		m.set(i,m.at(i)?setTo:1);
	    i++;
	    }; */
	m.NotifyAll(new NotifyMsg(m,Common.NM_MarkerChange));
        setUpdateRoot(1);
	repaint();	
    };
    public void mouseEntered(MouseEvent e) {};
    public void mouseExited(MouseEvent e) {};
    public void mouseDragged(MouseEvent e) 
    {
	if (drag) {
	    int x=e.getX(), y=e.getY();
	    if (x!=x2 || y!=y2) {
		x2=x; y2=y;
                setUpdateRoot(2);
		repaint();
	    };
	};
    };
    public void mouseMoved(MouseEvent ev) {};

    public void keyTyped(KeyEvent e) 
    {
	if (e.getKeyChar()=='l') run(this,"labels");
	if (e.getKeyChar()=='P') run(this,"print");
	if (e.getKeyChar()=='X') run(this,"exportPGS");
	if (e.getKeyChar()=='C') run(this,"exportCases");
	if (e.getKeyChar()=='e') run(this,"selRed");
	if (e.getKeyChar()=='j') run(this,"jitter");
    };
    public void keyPressed(KeyEvent e) {};
    public void keyReleased(KeyEvent e) {};

    public Object run(Object o, String cmd) {
	super.run(o,cmd);
        if (m!=null) m.run(o,cmd);
	if (cmd=="rotate") {
	    rotate();
	};
	if (cmd=="labels") {
	    showLabels=!showLabels;
	    MIlabels.setLabel((showLabels)?"Hide labels":"Show labels");
            setUpdateRoot(0);
            repaint();
	};
	if (cmd=="print") run(o,"exportPS");
	if (cmd=="exit") WinTracker.current.Exit();
        if (cmd=="selRed") { selRed=!selRed; setUpdateRoot(1); repaint(); };
        if (cmd=="jitter") {
            jitter=!jitter; setUpdateRoot(0); repaint();
        }
        if (cmd=="backlines") {  drawBackline=!drawBackline; setUpdateRoot(0); repaint(); }
        if (cmd=="YrangeDlg" || cmd=="XrangeDlg") {
            int rt=(cmd=="YrangeDlg")?1:0;
            Dialog d=intDlg=new Dialog(myFrame,(rt==1)?"Y range":"X range",true);
            IDlgCL ic=new IDlgCL(this);
            d.setBackground(Color.white);
            d.setLayout(new BorderLayout());
            d.add(new SpacingPanel(),BorderLayout.WEST);
            d.add(new SpacingPanel(),BorderLayout.EAST);
            Panel bp=new Panel(); bp.setLayout(new FlowLayout());
            Button b,b2;
            bp.add(b=new Button("OK"));bp.add(b2=new Button("Cancel"));
            d.add(bp,BorderLayout.SOUTH);
            d.add(new Label(" "),BorderLayout.NORTH);
            Panel cp=new Panel(); cp.setLayout(new FlowLayout());
            d.add(cp);
            cp.add(new Label("start: "));
            TextField tw=new TextField(""+A[rt].vBegin,6);
            TextField th=new TextField(""+(A[rt].vBegin+A[rt].vLen),6);
            cp.add(tw);
            cp.add(new Label(", end: "));
            cp.add(th);
            d.pack();
            b.addActionListener(ic);b2.addActionListener(ic);
            d.setVisible(true);
            if (!cancel) {
                double w=Tools.parseDouble(tw.getText());
                double h=Tools.parseDouble(th.getText());
                A[rt].setValueRange(w,h-w);
                setUpdateRoot(0);
                repaint();
            };
            d.dispose();
        };
        if (cmd=="exportCases") {
	    try {
		PrintStream p=Tools.getNewOutputStreamDlg(myFrame,"Export selected cases to ...","selected.txt");
		if (p!=null) {
		    p.println(v[0].getName()+"\t"+v[1].getName());
                    int i=0, sz=v[0].size();
                    while(i<sz) {
                        if (m.at(i)) {
                            Object oo=v[0].at(i);
                            p.println(((oo==null)?"NA":oo.toString())+"\t"+((v[1].at(i)==null)?"NA":v[1].at(i).toString()));
                        }
			i++;
		    }
		    p.close();
		}
	    } catch (Exception eee) {};
	}
	
	return null;
    };

    public void actionPerformed(ActionEvent e) {
	if (e==null) return;
	run(e.getSource(),e.getActionCommand());
    };
};
