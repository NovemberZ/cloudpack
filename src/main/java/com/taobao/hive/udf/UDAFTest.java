package com.taobao.hive.udf;

//��Ҫimport org.apache.hadoop.hive.ql.exec.UDAF
//�Լ�org.apache.hadoop.hive.ql.exec.UDAFEvaluator,
//�����������Ǳ����
import org.apache.hadoop.hive.ql.exec.UDAF;
import org.apache.hadoop.hive.ql.exec.UDAFEvaluator;
import org.apache.hadoop.io.Text;

//CREATE TEMPORARY FUNCTION count_test  AS 'com.taobao.hive.udf.UDAFTest';
//��������Ҫ�̳�UDAF��
 public class UDAFTest extends UDAF {
	//�ڲ���Evaluatorʵ��UDAFEvaluator�ӿ�
	public static class Evaluator implements UDAFEvaluator {

	  private int mCount; 
	  
      public Evaluator() {
          super();
          init();
      }
	  //Evaluator��Ҫʵ�� init��iterate��terminatePartial��merge��terminate�⼸������
	  //init���������ڹ��캯��������UDAF�ĳ�ʼ��.�����Խ��г�ʼ��
	  //iterate���մ���Ĳ������������ڲ�����ת���䷵������Ϊboolean
	  //terminatePartial�޲�������Ϊiterate������ת�����󣬷�����ת����
	  //merge����terminatePartial�ķ��ؽ������������merge�������䷵������Ϊboolean
	  //terminate�������յľۼ��������
	  public void init() {
		  mCount = 0;
	  } 

	  public boolean iterate(Text o) {
			if (o!=null)
			  mCount++; 
	
			return true;
	  } 

	  public Text terminatePartial() {
		    return new Text(mCount+"");
	  } 

	  public boolean merge(Text o) {
		    int a = Integer.parseInt(o.toString());
			mCount += a;
			return true;
	  } 

	  public Text terminate() {
		    return new Text(mCount+"");
	  }
   }
 }