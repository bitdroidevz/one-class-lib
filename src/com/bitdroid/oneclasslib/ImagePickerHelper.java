package com.bitdroid.oneclasslib;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.widget.Toast;

public abstract class ImagePickerHelper {
	
	public abstract void onImagePicked(boolean isSuccess, String filePath, Bitmap bmp);
	
	private final int REQUEST_PICKER_GALLERY = 296;
	private final int REQUEST_CAPTURE_CAMERA = 297;
	
	private final String SD_CARD_PATH = Environment.getExternalStorageDirectory() + File.separator;
	
	
	private Activity mActivity;
	private String filePath;
	private String parentFolder;

	public ImagePickerHelper(Activity act){
		mActivity = act;
		parentFolder = SD_CARD_PATH + act.getPackageName() ;
		checkDirectory();
	}
	
	public void pickGalleryImage(Fragment frag){
		Intent intent = new Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		intent.setType("image/*");
		if (frag != null)
			frag.startActivityForResult(intent, REQUEST_PICKER_GALLERY);
		else
			mActivity.startActivityForResult(intent, REQUEST_PICKER_GALLERY);
	}
	
	public void captureCameraImage(Fragment frag){
		Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		filePath = parentFolder
				+ File.separator + Calendar.getInstance().getTimeInMillis()
				+ ".jpg";
		intent.putExtra(MediaStore.EXTRA_OUTPUT,
				Uri.fromFile(new File(filePath)));
		
		if(frag != null)
		frag.startActivityForResult(intent, REQUEST_CAPTURE_CAMERA);
		else
		mActivity.startActivityForResult(intent, REQUEST_CAPTURE_CAMERA);
	}
	
	public boolean isValidRequest(int requestCode){
		if(requestCode == REQUEST_CAPTURE_CAMERA || requestCode == REQUEST_PICKER_GALLERY)
			return true;
		
		return false;
	}
	
	public void processResultIntent(int requestCode, Intent data){
		if(!isValidRequest(requestCode)){
			showToast("Invalid request");
			return;
		}
		//Log.d("RAVISH","req:"+requestCode + " ad:"+data.getData());
		if (requestCode == REQUEST_PICKER_GALLERY) {
			if (data != null && data.getDataString() != null) {
				String uri = data.getData().toString();
				if (uri.startsWith("content://media")) {
					filePath = getRealPathFromURI(data.getData());
					ImageProcessor processor = new ImageProcessor();
					processor.execute();
				} else if (uri.startsWith("file:///")) {
					filePath = uri.substring(7, uri.length());
					ImageProcessor processor = new ImageProcessor();
					processor.execute();
				} else {
					showToast("Invalid file type");
					return;
				}

			}
		}else if(requestCode == REQUEST_CAPTURE_CAMERA){
			ImageProcessor processor = new ImageProcessor();
			processor.execute();
		}
	}
	
	
	private class ImageProcessor extends AsyncTask<Void, Void, Bitmap>{
		
		private String file_Path;
		
		@Override
		protected Bitmap doInBackground(Void... arg0) {
			file_Path = parentFolder + File.separator
					+ getFileNameFromPath(filePath);
			copyFile(filePath, file_Path);
			return compressAndSaveImage(file_Path, 2);
		}

		private Bitmap compressAndSaveImage(String fileImage, int scale){
			try {
				ExifInterface exif = new ExifInterface(fileImage);
				String width = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH);
				String length = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH);
				int orientation = exif.getAttributeInt(
						ExifInterface.TAG_ORIENTATION,
						ExifInterface.ORIENTATION_NORMAL);
				int rotate = 0;
				if (true) {
					Log.i("RAVISH", "Before: " + width + "x" + length);
				}

				switch (orientation) {
				case ExifInterface.ORIENTATION_ROTATE_270:
					rotate = -90;
					break;
				case ExifInterface.ORIENTATION_ROTATE_180:
					rotate = 180;
					break;
				case ExifInterface.ORIENTATION_ROTATE_90:
					rotate = 90;
					break;
				}

				int w = Integer.parseInt(width);
				int l = Integer.parseInt(length);

				int what = w > l ? w : l;

				Options options = new Options();
				if (what > 1500) {
					options.inSampleSize = scale * 4;
				} else if (what > 1000 && what <= 1500) {
					options.inSampleSize = scale * 3;
				} else if (what > 400 && what <= 1000) {
					options.inSampleSize = scale * 2;
				} else {
					options.inSampleSize = scale;
				}
				if (true) {
					Log.i("RAVISH", "Scale: " + (what / options.inSampleSize));
					Log.i("RAVISH", "Rotate: " + rotate);
				}
				Bitmap bitmap = BitmapFactory.decodeFile(fileImage, options);
				File original = new File(fileImage);
				/*File file = new File(
						(original.getParent() + File.separator + original.getName()
								.replace(".", "_fact_" + scale + ".")));*/
				FileOutputStream stream = new FileOutputStream(original);
				if (rotate != 0) {
					Matrix matrix = new Matrix();
					matrix.setRotate(rotate);
					bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
							bitmap.getHeight(), matrix, false);
				}
				bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);

				
				stream.flush();
				stream.close();
				return bitmap;

			} catch (IOException e) {
				e.printStackTrace();
			    return null;
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		
		private void copyFile(String source, String dest) {
			if(source.equals(dest))
				return;
			
			InputStream is = null;
			OutputStream os = null;
			try {
				is = new FileInputStream(source);
				os = new FileOutputStream(dest);
				byte[] buffer = new byte[1024];
				int length;
				while ((length = is.read(buffer)) > 0) {
					os.write(buffer, 0, length);
				}

				is.close();
				os.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			if(result == null)
				onImagePicked(false, null, null);
			else
				onImagePicked(true, file_Path, result);
		}		
	}
	
	private void checkDirectory() {
		File directory = new File(parentFolder);
		if (!directory.exists()) {
			directory.mkdirs();
		}	
	}
	
	public void showOption(final Fragment frag) {
		final CharSequence[] items = { "Take Photo", "Choose from Gallery",
				"Cancel" };

		AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
		builder.setTitle("Upload photo !");
		builder.setItems(items, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int item) {
				if (items[item].equals("Take Photo")) {
					captureCameraImage(frag);
				} else if (items[item].equals("Choose from Gallery")) {
					pickGalleryImage(frag);
				} else if (items[item].equals("Cancel")) {
					dialog.dismiss();
				}
			}
		});
		builder.show();
	}
	
	private void showToast(String msg){
		Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
	}
	
	private String getFileNameFromPath(String absolutePath){
		int index = absolutePath.lastIndexOf(File.separator);
		return absolutePath.substring(index+1, absolutePath.length());
	}
	
	private String getRealPathFromURI(Uri contentURI) {
	    String result = null;
	    Cursor cursor = mActivity.getContentResolver().query(contentURI, null, null, null, null);
	    if (cursor != null) {
	        cursor.moveToFirst(); 
	        int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA); 
	        result = cursor.getString(idx);
	        cursor.close();
	    }
	    return result;
	}
}
