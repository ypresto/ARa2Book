/* 
 * PROJECT: ARa2Book
 * --------------------------------------------------------------------------------
 * Copyright (c)2010 Yuya Tanaka
 * yuya.tanaka.i@gmail.com
 * http://divsys.drm.doshisha.ac.jp/
 */

package jp.ac.doshisha.drm.divsys.ar2;

import java.awt.Frame;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;

import javax.media.Buffer;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCanvas;
import javax.media.opengl.GLEventListener;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import jp.nyatla.kGLModel.contentprovider.HttpContentProvider;
import jp.nyatla.nyarmqoviewer.utils.SimpleXPath;
import jp.nyatla.nyartoolkit.NyARException;
import jp.nyatla.nyartoolkit.core.NyARCode;
import jp.nyatla.nyartoolkit.core.param.NyARParam;
import jp.nyatla.nyartoolkit.core.transmat.NyARTransMatResult;
import jp.nyatla.nyartoolkit.detector.NyARDetectMarker;
import jp.nyatla.nyartoolkit.jmf.utils.JmfCaptureDevice;
import jp.nyatla.nyartoolkit.jmf.utils.JmfCaptureDeviceList;
import jp.nyatla.nyartoolkit.jmf.utils.JmfCaptureListener;
import jp.nyatla.nyartoolkit.jmf.utils.JmfNyARRaster_RGB;
import jp.nyatla.nyartoolkit.jogl.utils.NyARGLUtil;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.sun.opengl.util.Animator;
class Logger
{
    public static void logln(String i_message)
    {
	Date d=new Date();
	System.out.println("["+d+"]"+i_message);
    }
}
class ARa2BookParam
{
    public URL    base_url;
    public boolean is_resource = false;
    public String version;
    public String cparam_identifier;
    public ARa2Node[] node_array;
    public int screen_x;
    public int screen_y;
    public float frame_rate;
    public String comment;
	public double confidence;
    public ARa2BookParam(URL i_url) throws NyARException
    {
	Logger.logln("メタデータに接続中\r\n->"+i_url);
        this.base_url=i_url;
        // URL接続
	try{
	    InputStream in=HttpContentProvider.createInputStream(i_url);
            initByInputStream(in);
            in.close();
	}catch(Exception e){
	    throw new NyARException(e);
	}
    }
    public ARa2BookParam(URL i_url, boolean is_resource) throws NyARException
    {
	Logger.logln("メタデータに接続中\r\n->"+i_url);
	if (is_resource == true) {
		this.base_url = null;
		this.is_resource = true;
	} else {
		this.base_url = i_url;
		this.is_resource = false;
	}
	try{
	    InputStream in=HttpContentProvider.createInputStream(i_url);
            initByInputStream(in);
            in.close();
	}catch(Exception e){
	    throw new NyARException(e);
	}
    }
    private void initByInputStream(InputStream input_stream) throws NyARException
    {
	try{
	    //DOMの準備
	    DocumentBuilderFactory dbfactory = DocumentBuilderFactory.newInstance();
	    DocumentBuilder builder = dbfactory.newDocumentBuilder();
	    Document doc = builder.parse(input_stream);
	    //XPATHの準備
	    SimpleXPath xpath=new SimpleXPath(doc);

	    //値を読む
	    SimpleXPath root,tmp;
	    root=xpath.select("/root");

	    //Version読み出し
	    version=root.selectString("version");
	    //Config読み出し
	    tmp=root.select("config");
	    this.cparam_identifier=tmp.selectString("ar_param/camera_param_file");
	    this.screen_x=tmp.selectInt("ar_param/screen/x");
	    this.screen_y=tmp.selectInt("ar_param/screen/y");
	    this.frame_rate=(float)tmp.selectDouble("frame_rate");
	    this.confidence=tmp.selectDouble("min_confidence");
	    //Comment読み出し
	    comment = root.selectString("comment");

	    //Nodes読み出し
	    NodeList tmp_nl = root.selectNodeSet("nodes/node");
	    node_array = new ARa2Node[tmp_nl.getLength()];
	    for (int i = 0; i < node_array.length; i++) {
	    	ARa2Node node = new ARa2Node();
		    SimpleXPath tmp_node = new SimpleXPath(tmp_nl.item(i));
		    node.main_identifier = tmp_node.selectString("mqo_file");
		    node.arcode_identifier = tmp_node.selectString("ar_code/pattern_file");
		    node.marker_size = tmp_node.selectDouble("ar_code/size");
		    node.scale = (float) tmp_node.selectDouble("scale");
		    node.comment = tmp.selectString("comment");
		    if (is_resource == true) {
		    	URL main_url = ARa2BookParam.class.getResource(node.main_identifier);
		    	URL arcode_url = ARa2BookParam.class.getResource(node.arcode_identifier);
		    	if (main_url == null) {
		    		throw new NyARException("mqo file " + node.main_identifier + " not found (" + node.comment +")");
		    	}
		    	if (arcode_url == null) {
		    		throw new NyARException("ar code file " + node.arcode_identifier + " not found (" + node.comment +")");
		    	}
		    	node.main_identifier = main_url.toString();
		    	node.arcode_identifier = arcode_url.toString();
		    }
		    node.loadExternalData(base_url);
		    node_array[i] = node;
	    }

	}catch (Exception e){
	    throw new NyARException(e);
	}	
	//チェック
	this.validationCheck();
    }
    private void validationCheck() throws NyARException
    {
    	if(!version.equals("ARa2Book/0.3")){
    		throw new NyARException("バージョン不一致 ARa2Book/0.3である必要があります。");
    	}
    	for (int i = 0; i < node_array.length; i++) {
    		if(node_array[i].marker_size < 5.0f || node_array[i].marker_size > 1000.0){
    			throw new NyARException("ノード"+ i + ": マーカーサイズが不正 5.0<size<1000.0である必要があります。");
    		}
    		if(node_array[i].scale < 0.01 || this.node_array[i].scale > 100.0){
    			throw new NyARException("ノード"+ i + ": スケールが不正 0.01-100.0ぐらいを指定して下さい。");
    		}
    	}
    	if(	(this.screen_x == 160 && this.screen_y == 120) ||
    			(this.screen_x == 320 && this.screen_y == 240) ||
    			(this.screen_x == 640 && this.screen_y == 480)){
    	}else{
    		throw new NyARException("スクリーン幅が不正 160x120 320x240 640x480の何れかを指定してください。");
    	}
    	if(this.frame_rate < 10 || this.frame_rate > 60){
    		throw new NyARException("フレームレートが不正 15.0 30.0ぐらいを指定して下さい。");
    	}
    	if (this.confidence < 0 || this.confidence > 1) {
    		throw new NyARException("一致度が不正 0.0-1.0を指定して下さい。");
    	}
    }
}



public class ARa2Book implements GLEventListener,JmfCaptureListener
{
	static final String RESOURCE_PREFIX = "/";
	private int threshold;
    private ARa2BookParam app_param;
    private Animator animator;
    private JmfNyARRaster_RGB cap_image;
    private JmfCaptureDevice capture;
    private GL gl;
    //NyARToolkit関係
    private NyARGLUtil glnya;
    private NyARDetectMarker nya;
    private NyARParam ar_param;
    private Frame frame;

    /**
     * お手軽HttpInputStream生成関数
     * @return
     */
    private InputStream createHttpStream(URL i_base_url,String i_url) throws NyARException
    {
        // URL接続
	try{
	    URL url;
	    if(i_base_url!=null){
		url=new URL(i_base_url,i_url);
	    }else{
		url=new URL(i_url);		
	    }
	    return HttpContentProvider.createInputStream(url);
	}catch(Exception e){
	    throw new NyARException(e);
	}
    }


    public ARa2Book(ARa2BookParam i_param,int i_threshold) throws NyARException
    {
	System.setProperty("java.net.useSystemProxies", "true");

	Logger.logln("NyARMqoViewerAPPを開始しています...");
	this.threshold=i_threshold;
	this.app_param=i_param;
	int SCR_X=this.app_param.screen_x;
	int SCR_Y=this.app_param.screen_y;
	//キャプチャの準備
	Logger.logln("キャプチャデバイスを準備しています.");
	this.capture=(new JmfCaptureDeviceList()).getDevice(0);
	this.capture.setOnCapture(this);
	this.capture.setCaptureFormat(SCR_X, SCR_Y, app_param.frame_rate);
	//NyARToolkitの準備
	Logger.logln("NyARToolkitを準備しています.");
	this.ar_param=new NyARParam();
	this.ar_param.loadARParam(createHttpStream(this.app_param.base_url,this.app_param.cparam_identifier));
	this.ar_param.changeScreenSize(SCR_X,SCR_Y);
	// RGBラスタを作成
	this.cap_image=new JmfNyARRaster_RGB(this.ar_param, this.capture.getCaptureFormat());
	//マーカーDetecterの作成
	this.nya=createNyARDetectMarker();
	this.nya.setContinueMode(true);
	Logger.logln("ウインドウを作成しています.");
	//ウインドウの準備
	this.frame = new Frame("ARa2Book");
	GLCanvas canvas = new GLCanvas();
	frame.add(canvas);
	canvas.addGLEventListener(this);
	frame.addWindowListener(new WindowAdapter() {
	    public void windowClosing(WindowEvent e) {
		System.exit(0);
	    }
	});
	frame.setVisible(true);
	Insets ins=frame.getInsets();
	frame.setSize(SCR_X+ins.left+ins.right,SCR_Y+ins.top+ins.bottom);
	canvas.setBounds(ins.left,ins.top,SCR_X,SCR_Y);
    }
    public void init(GLAutoDrawable drawable)
    {
	Logger.logln("OpenGLを初期化しています.");
	gl = drawable.getGL();
	gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

	Logger.logln("モデルデータに接続中です.");
	try {
		for (int i = 0; i < app_param.node_array.length; i++) {
			app_param.node_array[i].createModelData(gl);
		}
	}
	catch(Exception e) {
	    e.printStackTrace() ;
	}
	//NyARToolkitの準備
	try{
	    //OpenGLユーティリティを作成
	    glnya=new NyARGLUtil(gl);
	    //キャプチャ開始
	    Logger.logln("キャプチャを開始します.");
	    capture.start();
	}catch(Exception e){
	    e.printStackTrace();
	}
	Logger.logln("拡張現実を開始します.");
	animator = new Animator(drawable);
	animator.start();
	Logger.logln("拡張現実の世界へようこそ！");

    }

    public void reshape(GLAutoDrawable drawable,
	    int x, int y,
	    int width, int height)
    {
	float ratio = (float)height / (float)width;
	gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
	gl.glViewport(0, 0,  width, height);

	//視体積の設定
	gl.glMatrixMode(GL.GL_PROJECTION);
	gl.glLoadIdentity();
	gl.glFrustum(-1.0f, 1.0f, -ratio, ratio,5.0f,40.0f);
	//見る位置
	gl.glMatrixMode(GL.GL_MODELVIEW);
	gl.glLoadIdentity();
	gl.glTranslatef(0.0f, 0.0f, -10.0f);
    }

    public void display(GLAutoDrawable drawable)
    {

    	try{
    		if(!cap_image.hasBuffer()){
    			return;
    		}    
    		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT); // Clear the buffers for new frame.          
    		//画像チェックしてマーカー探して、背景を書く
    		int num_marker_exist;
    		synchronized(cap_image){
    			num_marker_exist=nya.detectMarkerLite(cap_image,this.threshold);
    			//背景
    			glnya.drawBackGround(cap_image, 1.0);
    		}
    		//あったら立方体を書く
    		if(num_marker_exist > 0){
    			double[] glmat = new double[16];
    			gl.glMatrixMode(GL.GL_PROJECTION);
    			glnya.toCameraFrustumRH(ar_param, glmat);
    			gl.glLoadMatrixd(glmat, 0);
    			for (int i = 0; i < num_marker_exist; i++) {
    				if (nya.getConfidence(i) < app_param.confidence)
    					continue;
    				ARa2Node node = app_param.node_array[nya.getARCodeIndex(i)];
    				// Projection transformation.
    				gl.glMatrixMode(GL.GL_MODELVIEW);
    				// Viewing transformation.
    				NyARTransMatResult nya_transmat_result = new NyARTransMatResult();
    				nya.getTransmationMatrix(i, nya_transmat_result);
    				glnya.toCameraViewRH(nya_transmat_result, glmat);
    				gl.glLoadMatrixd(glmat,0);

    				// -------v------ kei add
    				gl.glTexEnvf(GL.GL_TEXTURE_ENV, GL.GL_TEXTURE_ENV_MODE, GL.GL_MODULATE);
    				gl.glTranslatef(0.0f,0.0f,0.0f) ;//位置調整
    				gl.glRotatef(90.0f,1.0f, 0.0f, 0.0f); //OpenGL座標系→ARToolkit座標系
    				gl.glEnable(GL.GL_CULL_FACE);
    				gl.glCullFace(GL.GL_FRONT);
    				node.model_data.enables(node.scale) ;
    				node.model_data.draw() ;
    				node.model_data.disables() ;
    				// -----^^^------ kei add
    			}
    		}
    		}catch(Exception e){
    			e.printStackTrace();
    		}
    	}
    public void onUpdateBuffer(Buffer i_buffer)
    {
	try{
	    synchronized(cap_image){
			cap_image.setBuffer(i_buffer);
	    }
	}catch(Exception e){
	    e.printStackTrace();
	}        
    }

    public void displayChanged(GLAutoDrawable drawable,
	    boolean modeChanged,
	    boolean deviceChanged)
    {
	
    }
    
    protected NyARDetectMarker createNyARDetectMarker() throws NyARException {
    	NyARCode[] ar_code_array = new NyARCode[app_param.node_array.length];
    	double[] marker_size_array = new double[app_param.node_array.length];
    	for(int i = 0; i < app_param.node_array.length; i++) {
    		ar_code_array[i] = app_param.node_array[i].ar_code;
    		marker_size_array[i] = app_param.node_array[i].marker_size;
    	}
    	return new NyARDetectMarker(ar_param, ar_code_array, marker_size_array, app_param.node_array.length, cap_image.getBufferType());
    }
    
    /**
     * 
     * @param args
     * １番目の引数に、XMLのURIを渡します。例えばhttp://127.0.0.1/model/miku.xmlとか。
     */
    public static void main(String[] args)
    {
	System.out.println("ARa2Book (c)2010 Yuya Tanaka");
	System.out.println("http://divsys.drm.doshisa.ac.jp/");
	System.out.println("Including these awesome products:");
	System.out.println("NyARMqoViewer Version 0.1 (c)2008 A虎＠nyatla.jp");
	System.out.println("http://nyatla.jp/nyartoolkit/");
	System.out.println("kGLModel (c)2008 kei");
	//System.out.println("");
	System.out.println();
	String target=null;
	int threshold=110;
	switch(args.length){
	case 1:
	    target=args[0];
	    break;
	case 2:
	    target=args[0];
	    threshold=Integer.parseInt(args[1]);
	    break;
	case 0:
		URL url = ARa2Book.class.getResource(ARa2Book.RESOURCE_PREFIX + "config.xml");
		if (url != null) {
			target = url.toString();
			break;
		}
	default:
	    System.err.println("引数には設定XMLのURLを設定してください。");
	    System.err.println("#ARa2Book [URL:設定xmlのurl] [カメラ閾値]");
	    System.exit(-1);
	}
	try{
	    // URL接続
	    ARa2BookParam param=new ARa2BookParam(new URL(target));
	    System.out.println("==メタデータの情報==\r\n"+param.comment);
	    new ARa2Book(param,threshold);
	} catch (Exception ex){
	    System.err.println("エラーになっちゃった。");
	    ex.printStackTrace();
	}
    }
}

