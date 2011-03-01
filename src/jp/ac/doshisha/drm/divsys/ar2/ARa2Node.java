package jp.ac.doshisha.drm.divsys.ar2;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import javax.media.opengl.GL;

import jp.nyatla.kGLModel.KGLException;
import jp.nyatla.kGLModel.KGLExtensionCheck;
import jp.nyatla.kGLModel.KGLModelData;
import jp.nyatla.kGLModel.contentprovider.ContentProvider;
import jp.nyatla.kGLModel.contentprovider.HttpContentProvider;
import jp.nyatla.nyartoolkit.NyARException;
import jp.nyatla.nyartoolkit.core.NyARCode;

public class ARa2Node {

	public KGLModelData model_data;
	public NyARCode ar_code;
	public double marker_size;
	public float scale;
	public String main_identifier;
	public ContentProvider main_content_provider;
	public String arcode_identifier;
	public ContentProvider arcode_content_provider;
	public String comment;
	private GL gl;

	public ARa2Node() throws NyARException {
		ar_code = new NyARCode(16, 16);
	}

	public void loadExternalData(URL i_base_url) throws KGLException, NyARException, MalformedURLException {
		URL main_url;
		URL arcode_url;
		if (i_base_url != null) {
			main_url = new URL(i_base_url, main_identifier);
			arcode_url = new URL(i_base_url, arcode_identifier);
		}
		else {
			main_url = new URL(main_identifier);
			arcode_url = new URL(arcode_identifier);
		}
		main_content_provider = new HttpContentProvider(main_url.toString());
		ar_code.loadARPatt(HttpContentProvider.createInputStream(arcode_url));
	}

	public void createModelData(GL i_gl) throws KGLException {
		gl = i_gl;
		model_data = KGLModelData.createGLModel(gl, null, main_content_provider,
	    		0.015f, KGLExtensionCheck.IsExtensionSupported(gl,"GL_ARB_vertex_buffer_object"));
	}
}
