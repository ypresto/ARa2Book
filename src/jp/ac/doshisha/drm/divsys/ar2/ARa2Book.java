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

import jp.nyatla.kGLModel.KGLExtensionCheck;
import jp.nyatla.kGLModel.KGLModelData;
import jp.nyatla.kGLModel.contentprovider.ContentProvider;
import jp.nyatla.kGLModel.contentprovider.HttpContentProvider;
import jp.nyatla.nyarmqoviewer.utils.SimpleXPath;
import jp.nyatla.nyartoolkit.NyARException;
import jp.nyatla.nyartoolkit.core.NyARCode;
import jp.nyatla.nyartoolkit.core.param.NyARParam;
import jp.nyatla.nyartoolkit.core.transmat.NyARTransMatResult;
import jp.nyatla.nyartoolkit.detector.NyARSingleDetectMarker;
import jp.nyatla.nyartoolkit.jmf.utils.JmfCaptureDevice;
import jp.nyatla.nyartoolkit.jmf.utils.JmfCaptureDeviceList;
import jp.nyatla.nyartoolkit.jmf.utils.JmfCaptureListener;
import jp.nyatla.nyartoolkit.jmf.utils.JmfNyARRaster_RGB;
import jp.nyatla.nyartoolkit.jogl.utils.NyARGLUtil;

import org.w3c.dom.Document;

import com.sun.opengl.util.Animator;
class Logger
{
    public static void logln(String i_message)
    {
	Date d=new Date();
	System.out.println("["+d+"]"+i_message);
    }
}
class NyARMqoViewerParam
{
    public URL    base_url;
    public String version;
    public String main_identifier;	//ContentProviderに渡すメイン識別子(moqファイルのアドレス)
    public String arcode_identifier;	//
    public String cparam_identifier;
    public double marker_size;
    public float scale;
    public int screen_x;
    public int screen_y;
    public float frame_rate;
    public String comment;
    public NyARMqoViewerParam(URL i_url) throws NyARException
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
	    this.arcode_identifier=tmp.selectString("ar_code/url");
	    this.marker_size=tmp.selectDouble("ar_code/size");
	    this.cparam_identifier=tmp.selectString("ar_param/url");
	    this.screen_x=tmp.selectInt("ar_param/screen/x");
	    this.screen_y=tmp.selectInt("ar_param/screen/y");
	    this.frame_rate=(float)tmp.selectDouble("frame_rate");

	    //content読み出し
	    tmp=root.select("content");
	    this.scale=(float)tmp.selectDouble("scale");
	    this.main_identifier=tmp.selectString("mqo_file");
	    this.comment=tmp.selectString("comment");

	}catch (Exception e){
	    throw new NyARException(e);
	}	
	//チェック
	this.validationCheck();
    }
    private void validationCheck() throws NyARException
    {
	if(version.compareTo("NyARMqoViewer/0.1")!=0){
	    throw new NyARException("バージョン不一致 NyARMqoViewer/0.1である必要があります。");
	}
	if(this.marker_size<5.0f || this.marker_size>1000.0){
	    throw new NyARException("マーカーサイズが不正 5.0<size<1000.0である必要があります。");
	}
	if(	(this.screen_x==160 && this.screen_y==120) ||
		(this.screen_x==320 && this.screen_y==240) ||
		(this.screen_x==640 && this.screen_y==480)){
	}else{
	    throw new NyARException("スクリーン幅が不正 160x120 320x240 640x480の何れかを指定してください。");
	}

	if(this.frame_rate<10 || this.frame_rate>60){
	    throw new NyARException("フレームレートが不正 15.0 30.0ぐらいを指定して下さい。");
	}
	if(this.scale<0.01 || this.scale>100.0){
	    throw new NyARException("スケールが不正 0.01-100.0ぐらいを指定して下さい。");
	}	
    }
}



public class ARa2Book implements GLEventListener,JmfCaptureListener
{
    private int threshold;
    private NyARMqoViewerParam app_param;
    private KGLModelData model_data; // kei add
    private ContentProvider content_provider;
    private Animator animator;
    private JmfNyARRaster_RGB cap_image;
    private JmfCaptureDevice capture;
    private GL gl;
    //NyARToolkit関係
    private NyARGLUtil glnya;
    private NyARSingleDetectMarker nya;
    private NyARParam ar_param;

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


    public ARa2Book(NyARMqoViewerParam i_param,int i_threshold) throws NyARException
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
	//NyARToolkitの準備
	Logger.logln("NyARToolkitを準備しています.");
	this.ar_param=new NyARParam();
	this.ar_param.loadARParam(createHttpStream(this.app_param.base_url,this.app_param.cparam_identifier));
	this.ar_param.changeScreenSize(SCR_X,SCR_Y);
	//検出マーカーの設定
	NyARCode ar_code  =new NyARCode(16,16);
	ar_code.loadARPatt(createHttpStream(this.app_param.base_url,this.app_param.arcode_identifier));
	//マーカーDetecterの作成
	this.capture.setCaptureFormat(SCR_X, SCR_Y, app_param.frame_rate);
	this.cap_image=new JmfNyARRaster_RGB(this.ar_param, this.capture.getCaptureFormat());
	this.nya=new NyARSingleDetectMarker(this.ar_param,ar_code,this.app_param.marker_size, cap_image.getBufferType());
	//コンテンツプロバイダを作成
	try{
	    this.content_provider=new HttpContentProvider(this.app_param.main_identifier);
	}catch(Exception e){
	    throw new NyARException(e);
	}
	Logger.logln("ウインドウを作成しています.");
	//ウインドウの準備
	Frame frame = new Frame("ARa2Book");
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
	try {// kei add
	    model_data = KGLModelData.createGLModel(gl,null,this.content_provider,0.015f,
		    KGLExtensionCheck.IsExtensionSupported(gl,"GL_ARB_vertex_buffer_object")) ;
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
	    boolean is_marker_exist;
	    synchronized(cap_image){
		is_marker_exist=nya.detectMarkerLite(cap_image,this.threshold);
		//背景
		glnya.drawBackGround(cap_image, 1.0);
	    }
	    //あったら立方体を書く
	    if(is_marker_exist){
		// Projection transformation.
		gl.glMatrixMode(GL.GL_PROJECTION);
		double[] glmat = new double[16];
		glnya.toCameraFrustumRH(ar_param, glmat);
		gl.glLoadMatrixd(glmat, 0);
		gl.glMatrixMode(GL.GL_MODELVIEW);
		// Viewing transformation.
		gl.glLoadIdentity();
		NyARTransMatResult nya_transmat_result = new NyARTransMatResult();
		nya.getTransmationMatrix(nya_transmat_result);
		glnya.toCameraViewRH(nya_transmat_result, glmat);
		gl.glLoadMatrixd(glmat,0);

		// -------v------ kei add
		gl.glTexEnvf(GL.GL_TEXTURE_ENV, GL.GL_TEXTURE_ENV_MODE, GL.GL_MODULATE);
		gl.glTranslatef(0.0f,0.0f,0.0f) ;//位置調整
		gl.glRotatef(90.0f,1.0f, 0.0f, 0.0f); //OpenGL座標系→ARToolkit座標系
		gl.glEnable(GL.GL_CULL_FACE);
		gl.glCullFace(GL.GL_FRONT);
		model_data.enables(app_param.scale) ;
		model_data.draw() ;
		model_data.disables() ;
		// -----^^^------ kei add
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
	int threshold=0;
	switch(args.length){
	case 1:
	    threshold=110;
	    target=args[0];
	    break;
	case 2:
	    target=args[0];
	    threshold=Integer.parseInt(args[1]);
	    break;
	default:
	    System.err.println("引数には設定XMLのURLを設定してください。");
	    System.err.println("#ARa2Book [URL:設定xmlのurl] [カメラ閾値]");
	    System.exit(-1);
	}
	try{
	    // URL接続
	    NyARMqoViewerParam param=new NyARMqoViewerParam(new URL(target));
	    System.out.println("==モデルデータの情報==\r\n"+param.comment);
	    new ARa2Book(param,threshold);
	} catch (Exception ex){
	    System.err.println("エラーになっちゃった。");
	    ex.printStackTrace();
	}
    }
}

