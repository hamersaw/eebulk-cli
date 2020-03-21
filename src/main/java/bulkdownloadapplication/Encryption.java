package bulkdownloadapplication;

import java.util.ArrayList;

public class Encryption {
   private static Encryption _instance = null;
   private String _scramble1 = "! #$%&()*,-.+/0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{|}~";
   private String _scramble2 = "f^jAE]okIOzU[2&q1{3`h5w_794p@6s8?BgP>dFV=m D<TcS%Ze|r:lGK/uCy.Jx)HiQ!#$~(;Lt-R}Ma,Nv+WYnb*0X";
   private double _adj;
   private int _mod;
   private String _defaultEncryptionKey = "JNFjkf785jkfJR34";
   private String _saltValue = "sd554As5A";

   public Encryption() throws EncryptionException {
      if (this._scramble1.length() != this._scramble2.length()) {
         throw new EncryptionException("Scramble 1 and scramble two MUST be the same length");
      } else {
         this._adj = 1.75D;
         this._mod = 3;
      }
   }

   private String _decrypt(String var1, String var2) throws EncryptionException {
      try {
         double[] var3 = this._convertKey(var1);
         if (var2.isEmpty()) {
            throw new EncryptionException("No value has been supplied for decryption");
         } else {
            String var4 = "";
            double var5 = 0.0D;

            for(int var7 = 0; var7 < var2.length(); ++var7) {
               char var8 = var2.charAt(var7);
               int var9 = this._scramble2.indexOf(var8);
               if (var9 == -1) {
                  throw new EncryptionException("Source string contains an invalid character");
               }

               double var10 = this._applyFudge(var3);
               double var12 = var5 + var10;
               double var14 = (double)((long)var9 - Math.round(var12));
               var14 = this._checkRange(var14);
               var5 = var12 + (double)var9;
               var4 = var4 + this._scramble1.charAt((int)var14);
            }

            return var4;
         }
      } catch (Exception var16) {
         throw new EncryptionException(var16.getMessage());
      }
   }

   private String _encrypt(String var1, String var2, int var3) throws EncryptionException {
      try {
         double[] var4 = this._convertKey(var1);
         if (var2.isEmpty()) {
            throw new EncryptionException("No value has been supplied for encryption");
         } else {
            String var5 = "";
            double var6 = 0.0D;

            for(int var8 = 0; var8 < var2.length(); ++var8) {
               char var9 = var2.charAt(var8);
               double var10 = (double)this._scramble1.indexOf(var9);
               if (var10 == -1.0D) {
                  throw new EncryptionException("Source string contains an invalid character");
               }

               double var12 = this._applyFudge(var4);
               double var14 = var6 + var12;
               double var16 = (double)Math.round(var14) + var10;
               var16 = this._checkRange(var16);
               var6 = var14 + var16;
               var5 = var5 + this._scramble2.charAt((int)var16);
            }

            return var5;
         }
      } catch (Exception var18) {
         throw new EncryptionException(var18.getMessage());
      }
   }

   public double getAdjustment() {
      return this._adj;
   }

   public int getModulus() {
      return this._mod;
   }

   public void setAdjustment(double var1) {
      this._adj = var1;
   }

   public void setModulus(int var1) {
      this._mod = var1;
   }

   private double _applyFudge(double[] var1) {
      double var2 = var1[0];
      var2 += this._adj;

      for(int var4 = 1; var4 < var1.length; ++var4) {
         var1[var4 - 1] = var1[var4];
      }

      var1[var1.length - 1] = var2;
      if (this._mod != 0 && (int)var2 % this._mod == 0) {
         var2 *= -1.0D;
      }

      return var2;
   }

   private double _checkRange(double var1) {
      double var3 = (double)Math.round(var1);

      int var5;
      for(var5 = this._scramble1.length(); var3 >= (double)var5; var3 -= (double)var5) {
      }

      while(var3 < 0.0D) {
         var3 += (double)var5;
      }

      return var3;
   }

   private double[] _convertKey(String var1) throws EncryptionException {
      if (var1.isEmpty()) {
         throw new EncryptionException("No value has been supplied for the encryption");
      } else {
         ArrayList var2 = new ArrayList();
         var2.add((double)var1.length() * 1.0D);
         double var3 = 0.0D;

         for(int var5 = 0; var5 < var1.length(); ++var5) {
            char var6 = var1.charAt(var5);
            int var7 = this._scramble1.indexOf(var6);
            if (var7 == -1) {
               throw new EncryptionException("Key contains an invalid character (" + var6 + ")");
            }

            var3 += (double)var7;
            var2.add((double)var7 * 1.0D);
         }

         var2.add(var3);
         double[] var8 = new double[var2.size()];

         for(int var9 = 0; var9 < var2.size(); ++var9) {
            var8[var9] = (Double)var2.get(var9);
         }

         return var8;
      }
   }

   public static Encryption getInstance() throws EncryptionException {
      if (_instance == null) {
         _instance = new Encryption();
      }

      return _instance;
   }

   public String encrypt(String var1) throws EncryptionException {
      String var2 = var1.substring(0, var1.length() / 2);
      String var3 = var1.substring(var1.length() / 2);
      String var4 = var2 + this._saltValue + var3;
      return this._encrypt(this._defaultEncryptionKey, var4, 0);
   }

   public String decrypt(String var1) throws EncryptionException {
      String var2 = this._decrypt(this._defaultEncryptionKey, var1);
      return var2.replaceAll(this._saltValue, "");
   }
}
