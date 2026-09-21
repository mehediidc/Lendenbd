package com.mhexpenses.bangla;
import android.print.*;import android.os.*;import android.webkit.*;import android.content.*;
public class WebPrintAdapter extends PrintDocumentAdapter{
 Context ctx;String html;WebView web;
 public WebPrintAdapter(Context c,String h){ctx=c;html=h;}
 public void onLayout(PrintAttributes a,PrintAttributes b,CancellationSignal s,LayoutResultCallback cb,Bundle e){
  web=new WebView(ctx);web.getSettings().setDefaultTextEncodingName("UTF-8");web.loadDataWithBaseURL(null,html,"text/html","UTF-8",null);
  new Handler().postDelayed(()->cb.onLayoutFinished(new PrintDocumentInfo.Builder("income-expense.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(),true),800);}
 public void onWrite(PageRange[] p,ParcelFileDescriptor f,CancellationSignal s,WriteResultCallback cb){web.createPrintDocumentAdapter("doc").onWrite(p,f,s,cb);}
}