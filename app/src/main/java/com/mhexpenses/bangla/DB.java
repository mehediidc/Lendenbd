package com.mhexpenses.bangla;
import android.content.*;import android.database.*;import android.database.sqlite.*;
public class DB extends SQLiteOpenHelper{
 public DB(Context c){super(c,"income_expense.db",null,2);}
 public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE tx(id INTEGER PRIMARY KEY AUTOINCREMENT,type INTEGER,category TEXT,amount REAL,note TEXT,date TEXT)");d.execSQL("CREATE TABLE cat(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE,type INTEGER)");String[] e={"খাবার","যাতায়াত","বাজার","বিল","শিক্ষা","চিকিৎসা","অন্যান্য"};String[] i={"বেতন","ব্যবসা","অন্যান্য আয়"};for(String x:e)addCat(d,x,2);for(String x:i)addCat(d,x,1);}
 void addCat(SQLiteDatabase d,String n,int t){ContentValues v=new ContentValues();v.put("name",n);v.put("type",t);d.insert("cat",null,v);}
 public void onUpgrade(SQLiteDatabase d,int o,int n){if(o<2){d.execSQL("CREATE TABLE cat(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE,type INTEGER)");}}
 public long add(int t,String c,double a,String note,String date){ContentValues v=new ContentValues();v.put("type",t);v.put("category",c);v.put("amount",a);v.put("note",note);v.put("date",date);return getWritableDatabase().insert("tx",null,v);}
 public void update(long id,int t,String c,double a,String note,String date){ContentValues v=new ContentValues();v.put("type",t);v.put("category",c);v.put("amount",a);v.put("note",note);v.put("date",date);getWritableDatabase().update("tx",v,"id=?",new String[]{""+id});}
 public void delete(long id){getWritableDatabase().delete("tx","id=?",new String[]{""+id});}
 public Cursor all(){return getReadableDatabase().query("tx",new String[]{"id","type","category","amount","note","date"},null,null,null,null,"id DESC");}
 public Cursor categories(int type){return getReadableDatabase().query("cat",new String[]{"name"},"type=?",new String[]{""+type},null,null,"name ASC");}
 public void addCategory(String n,int type){try{addCat(getWritableDatabase(),n,type);}catch(Exception e){}}
 public double sum(int t){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM tx WHERE type=?",new String[]{""+t});c.moveToFirst();double x=c.getDouble(0);c.close();return x;}
 public Cursor expenseByCat(){return getReadableDatabase().rawQuery("SELECT category,SUM(amount) FROM tx WHERE type=2 GROUP BY category ORDER BY SUM(amount) DESC",null);}
 public void clearAll(){getWritableDatabase().delete("tx",null,null);}
}