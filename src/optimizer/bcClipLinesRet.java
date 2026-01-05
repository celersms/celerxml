// Permission is granted, free of charge, to any person obtaining a copy of this software and associated
// documentation, to deal in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
// to permit persons to whom the Software is furnished to do so, subject to the condition that this
// copyright shall be included in all copies or substantial portions of the Software:
// Copyright Victor Celer, 2025 - 2026

import java.io.File;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;

// This tool optimizes the CelerXML classfiles by removing line numbers
// for unreachable return code at the end of specific methods, for example:
//
//      ***
//      throwException("some error message");
//      return 0; // line number for this code can be removed
//   }
//
public final class bcClipLinesRet{

   // Handle blocks up to 16K
   private static final byte[] buf = new byte[16384];

   // Temporary classfile
   private static final File tmpfile = new File("tmp.class");

   public static final void main(String[] args){

      // Optionally supply the directory where the classfiles tree is stored
      String fdir = null;
      if(args.length > 0)
         fdir = args[0];

      // Read the list of classes and methods to optimize from stdin
      ArrayList<String> mth = new ArrayList<String>();
      int total_saved = 0;
      try{
         BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
         String line;
         while((line = br.readLine()) != null){
            int previdx, len, idx = line.indexOf('#');
            if(idx >= 0)
               line = line.substring(0, idx);
            if((line = line.trim()).length() == 0 || (idx = line.indexOf(':')) <= 0)
               continue;
            String ss, methods = line.substring(idx + 1);
            line = line.substring(0, idx).trim();
            mth.clear();
            previdx = 0;
            len = methods.length();
            do{
               if((idx = methods.indexOf(',', previdx)) < 0)
                  idx = len;
               if((ss = methods.substring(previdx, idx).trim()).length() != 0)
                  mth.add(ss);
            }while((previdx = idx + 1) < len);
            try{
               if((len = optimize(new File(fdir, line.replace('.', File.separatorChar) + ".class"), mth.toArray())) > 0){
                  total_saved += len;
                  System.out.println(line + " updated, bytes removed: " + len);
               }else
                  System.err.println("ERR: Failed to update " + line);
            }catch(Exception ex){
               System.err.println("ERR: Failed to optimize " + line + " due to " + ex);
            }
         }
      }catch(IOException ex){
         System.err.println("ERR: Failed to read from stdin due to " + ex);
      }
      if(total_saved > 0)
         System.out.println("Total bytes removed: " + total_saved);
   }

   private static final int optimize(File classfile, Object[] mth) throws Exception{
      int mth_len = mth.length, saved_bytes = 0;
      int[] mth_idx = new int[mth_len];
      DataInputStream dis = null;
      DataOutputStream dos = null;
      try{
         int u4, u2, u1, len, xx, const_pool_size, const_pool_idx, code_idx = 0, lines_idx = 0;
         dis = new DataInputStream(new BufferedInputStream(new FileInputStream(classfile)));
         dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmpfile)));
         if((u4 = dis.readInt()) != 0xCAFEBABE) // magic
            throw new Exception("Unrecognized magic");
         dos.writeInt(u4);
         dos.writeInt(dis.readInt()); // minor_version, major_version
         if((len = dis.readUnsignedShort()) < 1) // constant_pool_count
            throw new Exception("Invalid constant pool size: " + len);
         dos.writeShort(len);
         const_pool_size = len;
         const_pool_idx = 0;
         boolean bb = false;
         while(++const_pool_idx < len){
            dos.writeByte(u1 = dis.readUnsignedByte()); // tag
            switch(u1){
               case 1:  // UTF8
                  String utf = dis.readUTF();
                  if(utf == null)
                     throw new Exception("Invalid UTF8");
                  for(int ii = 0; ii < mth_len; ii++)
                     if(utf.equals(mth[ii])){
                        mth_idx[ii] = const_pool_idx;
                        bb = true;
                        break;
                     }
                  if("Code".equals(utf))
                     code_idx = const_pool_idx;
                  if("LineNumberTable".equals(utf))
                     lines_idx = const_pool_idx;
                  dos.writeUTF(utf);
                  continue;
               case 5:  // Long
               case 6:  // Double
                  dos.writeInt(dis.readInt()); // high_bytes
                  dos.writeInt(dis.readInt()); // low_bytes
                  continue;
               case 7:  // Class
               case 8:  // String
                  dos.writeShort(dis.readUnsignedShort()); // name_index | string_index
                  continue;
               case 3:  // Integer
               case 4:  // Float
               case 9:  // Filedref
               case 10: // Methodref
               case 11: // InterfaceMethodref
               case 12: // NameAndType
                  dos.writeInt(dis.readInt()); // bytes | class_index, name_and_type_index | name_index, descriptor_index
                  continue;
               default:
                  throw new Exception("Unsupported constant pool tag: " + u1);
            }
         }
         if(code_idx == 0)
            throw new Exception("'Code' entry not found");
         if(!bb)
            throw new Exception("No methods match: " + mth);
         dos.writeInt(dis.readInt()); // access_flags, this_class
         dos.writeShort(dis.readUnsignedShort()); // super_class
         if((len = dis.readUnsignedShort()) < 0 || len >= 8192) // interface_count
            throw new Exception("Invalid interfaces count: " + len);
         dos.writeShort(len);
         if((len <<= 1) != 0){
            if(dis.read(buf, 0, len) != len)
               throw new Exception("Failed to read interfaces array");
            dos.write(buf, 0, len);
         }
         if((len = dis.readUnsignedShort()) < 0) // fields_count
            throw new Exception("Invalid fields count: " + len);
         dos.writeShort(len);
         while(len-- > 0){
            dos.writeInt(dis.readInt()); // access_flags, name_index
            dos.writeShort(dis.readUnsignedShort()); // descriptor_index
            if((u2 = dis.readUnsignedShort()) < 0) // attributes_count
               throw new Exception("Invalid attributes count: " + u2);
            dos.writeShort(u2);
            while(u2-- > 0){
               dos.writeShort(dis.readUnsignedShort()); // attribute_name_index
               if((u1 = dis.readInt()) < 0 || u1 >= 16384) // attribute_length
                  throw new Exception("Invalid attribute length: " + u1);
               dos.writeInt(u1);
               if(u1 != 0){
                  if(dis.read(buf, 0, u1) != u1) // attribute
                     throw new Exception("Failed to read attribute");
                  dos.write(buf, 0, u1);
               }
            }
         }
         if((len = dis.readUnsignedShort()) < 0) // methods_count
            throw new Exception("Invalid methods count: " + len);
         dos.writeShort(len);
         while(len-- > 0){
            dos.writeShort(dis.readUnsignedShort()); // access_flags
            if((u2 = dis.readUnsignedShort()) < 1 || u2 >= const_pool_size) // name_index
               throw new Exception("Invalid name index: " + u2);
            dos.writeShort(u2);
            bb = false;
            for(int ii = 0; ii < mth_len; ii++)
               if(mth_idx[ii] == u2){
                  bb = true;
                  break;
               }
            dos.writeShort(dis.readUnsignedShort()); // descriptor_index
            if((u2 = dis.readUnsignedShort()) < 0) // attributes_count
               throw new Exception("Invalid attributes count: " + u2);
            dos.writeShort(u2);
            while(u2-- > 0){
               if((u1 = dis.readUnsignedShort()) < 1 || u1 >= const_pool_size) // attribute_name_index
                  throw new Exception("Invalid attribute name index: " + u1);
               dos.writeShort(u1);
               if((xx = dis.readInt()) < 0 || xx >= 16384) // attribute_length
                  throw new Exception("Invalid attribute length: " + xx);
               if(xx != 0){
                  if(dis.read(buf, 0, xx) != xx) // attribute
                     throw new Exception("Failed to read bytecode");

                  // Check for return bytecode
                  if(u1 == code_idx && bb){
                     int ee, ix, yy = buf[4] << 24 | (buf[5] & 0xFF) << 16 | (buf[6] & 0xFF) << 8 | buf[7] & 0xFF;
                     if(yy + 8 > xx)
                        throw new Exception("Invalid code length: " + yy);
                     if(((ix = buf[yy + 6]) >= 2 && ix <= 6 && buf[yy + 7] == (byte)0xAC) || // iconst_m1/iconst_0/iconst_1/iconst_2/iconst_3, ireturn
                        ((ix == 1 || ix == (byte)0x2A) && buf[yy + 7] == (byte)0xB0)){       // aconst_null/aload_0, areturn
                        ix = yy + 8;
                        ee = (buf[ix++] & 0xFF) << 8 | buf[ix++] & 0xFF; // exception_table_length
                        ix += ee << 3;
                        int attr_cnt = ix;
                        int num_attr = ee = (buf[ix++] & 0xFF) << 8 | buf[ix++] & 0xFF; // attributes_count
                        while(ee-- > 0){
                           int uu = (buf[ix++] & 0xFF) << 8 | buf[ix++] & 0xFF; // attribute_name_index
                           int attr_mrk = ix;
                           int attr_len = buf[ix++] << 24 | (buf[ix++] & 0xFF) << 16 | (buf[ix++] & 0xFF) << 8 | buf[ix++] & 0xFF; // attribute_length
                           int attr_nxt = ix + attr_len;

                           // Remove line numbers corresponding to return bytecode
                           if(uu != 0 && uu == lines_idx){
                              uu = (buf[ix++] & 0xFF) << 8 | buf[ix++] & 0xFF; // line_number_table_length
                              for(int ii = 0; ii < uu; ii++)
                                 if(((buf[ix] & 0xFF) << 8 | buf[ix + 1] & 0xFF) >= yy - 2){ // start_pc
                                    if(--uu == 0){

                                       // Remove the whole attribute because it's empty
                                       System.arraycopy(buf, attr_nxt, buf, attr_mrk - 2, xx - attr_nxt);
                                       uu = attr_nxt - attr_mrk + 2;
                                       xx -= uu;
                                       saved_bytes += uu;
                                       attr_nxt = attr_mrk - 2;
                                       num_attr--;
                                       buf[attr_cnt]     = (byte)(num_attr >> 8);
                                       buf[attr_cnt + 1] = (byte)num_attr;
                                       break;
                                    }

                                    // Remove 1 line
                                    xx -= 4;
                                    System.arraycopy(buf, ix + 4, buf, ix, xx - ix);
                                    saved_bytes += 4;
                                    attr_nxt -= 4;
                                    attr_len -= 4;
                                    buf[attr_mrk]     = (byte)(attr_len >> 24);
                                    buf[attr_mrk + 1] = (byte)(attr_len >> 16);
                                    buf[attr_mrk + 2] = (byte)(attr_len >> 8);
                                    buf[attr_mrk + 3] = (byte)attr_len;
                                    buf[attr_mrk + 4] = (byte)(uu >> 8);
                                    buf[attr_mrk + 5] = (byte)uu;
                                 }else
                                    ix += 4;
                           }
                           ix = attr_nxt;
                        }
                     }
                  }
                  dos.writeInt(xx);
                  dos.write(buf, 0, xx);
               }else
                  dos.writeInt(0);
            }
         }
         if((u2 = dis.readUnsignedShort()) < 0) // attributes_count
            throw new Exception("Invalid attributes count: " + u2);
         dos.writeShort(u2);
         while(u2-- > 0){
            dos.writeShort(dis.readUnsignedShort()); // attribute_name_index
            if((u1 = dis.readInt()) < 0 || u1 >= 16384) // attribute_length
               throw new Exception("Invalid attribute length: " + u1);
            dos.writeInt(u1);
            if(u1 != 0){
               if(dis.read(buf, 0, u1) != u1) // attribute
                  throw new Exception("Failed to read attribute");
               dos.write(buf, 0, u1);
            }
         }
      }catch(Exception ex){
         saved_bytes = 0;
         throw ex;
      }finally{
         if(dis != null)
            try{
               dis.close();
            }catch(Exception ex){ /* NOOP */ }
         if(dos != null)
            try{
               dos.close();
            }catch(Exception ex){ /* NOOP */ }
      }

      // Overwrite the classfile only if any bytes saved
      if(saved_bytes > 0 && classfile.delete() && tmpfile.renameTo(classfile))
         return saved_bytes;

      // Delete the temporary file otherwise
      tmpfile.delete();
      return 0;
   }
}
