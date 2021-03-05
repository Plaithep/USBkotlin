package th.co.srichand.usbtestkotlin


import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.usb.*
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import fr.w3blog.zpl.constant.ZebraFont
import fr.w3blog.zpl.model.ZebraLabel
import fr.w3blog.zpl.model.element.ZebraBarCode39
import fr.w3blog.zpl.model.element.ZebraNativeZpl
import fr.w3blog.zpl.model.element.ZebraText
import java.nio.charset.StandardCharsets
import java.util.*


class MainActivity : Activity() {
    var mUsbManager: UsbManager? = null
    var mDevice: UsbDevice? = null
    var mConnection: UsbDeviceConnection? = null
    var mInterface: UsbInterface? = null
    var mEndPoint: UsbEndpoint? = null
    var mPermissionIntent: PendingIntent? = null
    var ed_txt: TextView? = null
    val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    val forceCLaim = true
    val mImageView: ImageView? = null
    var testingData: String? = null
    var mDeviceList: HashMap<String?, UsbDevice>? = null
    var mDeviceIterator: Iterator<UsbDevice>? = null
    lateinit var testBytes: ByteArray
    private val STORAGE_PERMISSION_CODE: Int =1000
    private val READ_PERMISSION_CODE: Int = 1001
    private val IMAGE_PICK_CODE : Int = 1000
    private val PDF_PICK_CODE : Int = 1002


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val print = findViewById<View>(R.id.print) as Button


        val zebraLabel = ZebraLabel(812, 812)
        zebraLabel.setDefaultZebraFont(ZebraFont.ZEBRA_ZERO)

        zebraLabel.addElement( ZebraNativeZpl("^KD0\n"));

        println("@Zebralabel"+zebraLabel.zplCode)

        val getImage = findViewById<View>(R.id.getImage) as Button
        val getPDF = findViewById<View>(R.id.getPDF) as Button
        getImage.setOnClickListener { getImage() }
        getPDF.setOnClickListener{ getPDF() }


        mUsbManager = getSystemService(Context.USB_SERVICE) as UsbManager?
        mDeviceList = mUsbManager?.deviceList
        if (mDeviceList?.size!! > 0) {
            mDeviceIterator = mDeviceList!!.values.iterator()
            Toast.makeText(
                    this,
                    "Device List Size: " + mDeviceList!!.size.toString(),
                    Toast.LENGTH_SHORT
            ).show()
            val textView = findViewById<View>(R.id.usbDevice) as TextView
            var usbDevice = ""
            while ((mDeviceIterator as MutableIterator<UsbDevice>).hasNext()) {
                val usbDevice1 = (mDeviceIterator as MutableIterator<UsbDevice>).next()
                usbDevice += """
            Product Name: ${usbDevice1.productName}
                """
                val interfaceCount = usbDevice1.interfaceCount
                Toast.makeText(this, "INTERFACE COUNT: $interfaceCount", Toast.LENGTH_SHORT).show()
                mDevice = usbDevice1
                Toast.makeText(this, "Device is attached", Toast.LENGTH_SHORT).show()
                textView.text = usbDevice
            }
            mPermissionIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    Intent(ACTION_USB_PERMISSION),
                    0
            )
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            registerReceiver(mUsbReceiver, filter)
            mUsbManager!!.requestPermission(mDevice, mPermissionIntent)
        } else {
            Toast.makeText(this, "Please attach printer via USB", Toast.LENGTH_SHORT).show()
        }
        print.setOnClickListener { print(mConnection, mInterface) }
    }


    private fun getZplCode(bitmap: Bitmap, addHeaderFooter: Boolean): String? {
        val zp = ZPLConverter()
        zp.setCompressHex(true)
        zp.setBlacknessLimitPercentage(50)
        val grayBitmap: Bitmap = toGrayScale(bitmap)
        return zp.convertFromImage(grayBitmap, addHeaderFooter)
    }

    private fun toGrayScale(bmpOriginal: Bitmap): Bitmap {
        val height: Int = bmpOriginal.height
        val width: Int = bmpOriginal.width
        val grayScale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(grayScale!!)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        val f = ColorMatrixColorFilter(cm)
        paint.colorFilter = f
        c.drawBitmap(bmpOriginal, 0f, 0f, paint)
        return grayScale
    }


    private fun print(connection: UsbDeviceConnection?, usbInterface: UsbInterface?) {
        if (usbInterface == null) {
            Toast.makeText(this, "INTERFACE IS NULL", Toast.LENGTH_SHORT).show()
        } else if (connection == null) {
            Toast.makeText(this, "CONNECTION IS NULL", Toast.LENGTH_SHORT).show()
        } else if (forceCLaim == null) {
            Toast.makeText(this, "FORCE CLAIM IS NULL", Toast.LENGTH_SHORT).show()
        } else {
            connection.claimInterface(usbInterface, forceCLaim)
            val thread = Thread {
               // val cut_paper = byteArrayOf(0x1D, 0x56, 0x41, 0x10)
                connection.bulkTransfer(mEndPoint, testBytes, testBytes.size, 0)
               // connection.bulkTransfer(mEndPoint, cut_paper, cut_paper.size, 0)
            }
            thread.run()
        }
    }


    private val mUsbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val device = intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as UsbDevice?
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            //call method to set up device communication
                            mInterface = device.getInterface(0)
                            mEndPoint = mInterface!!.getEndpoint(1) // 0 IN and  1 OUT to printer.
                            mConnection = mUsbManager?.openDevice(device)
                        }
                    } else {
                        Toast.makeText(
                                context,
                                "PERMISSION DENIED FOR THIS DEVICE",
                                Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }


    private fun  getImage(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED){
                // show pop up
                requestPermissions(
                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                        READ_PERMISSION_CODE
                )
            }
            else{
                getListImages()
            }
        }
        else{
            getListImages()
        }
    }

    private fun getListImages(){
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT;
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }


    private fun getPDF(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED){
                // show pop up
                requestPermissions(
                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                        READ_PERMISSION_CODE
                )
            }
            else{
                getListPDF()
            }
        }
        else{
            getListPDF()
        }
    }

    private fun getListPDF(){
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "application/pdf"
        intent.action = Intent.ACTION_GET_CONTENT;
        startActivityForResult(intent, PDF_PICK_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == RESULT_OK && requestCode == IMAGE_PICK_CODE){
            val stream = data?.data?.let { contentResolver.openInputStream(it) }
            val bitmap = BitmapFactory.decodeStream(stream)

            testingData = getZplCode(bitmap, true)
            testBytes = testingData!!.toByteArray(StandardCharsets.UTF_8)
            ed_txt = findViewById<View>(R.id.ed_txt) as TextView?

            val s = String(testBytes, StandardCharsets.UTF_8)
            ed_txt?.text = s
        }
        else if(resultCode == RESULT_OK && requestCode == PDF_PICK_CODE){
                val stream = data?.data?.let { contentResolver.openInputStream(it) }
                ed_txt = findViewById<View>(R.id.ed_txt) as TextView?
                println(data?.data)
                val pd: PDDocument = PDDocument.load(stream)
                val pr = PDFRenderer(pd)
                val bitmap = pr.renderImageWithDPI(0, 203F)
                val view = findViewById<ImageView>(R.id.resultBitmap)
                //view.setImageBitmap(bitmap)
                println("@stearm $stream")
    //            testingData = getZplCode(bitmap, true)
    //            testBytes = testingData!!.toByteArray(StandardCharsets.UTF_8)
           //     ed_txt?.setText(testingData)


        }
    }









    private fun translateDeviceClass(deviceClass: Int): String? {
        return when (deviceClass) {
            UsbConstants.USB_CLASS_APP_SPEC -> "Application specific USB class"
            UsbConstants.USB_CLASS_AUDIO -> "USB class for audio devices"
            UsbConstants.USB_CLASS_CDC_DATA -> "USB class for CDC devices (communications device class)"
            UsbConstants.USB_CLASS_COMM -> "USB class for communication devices"
            UsbConstants.USB_CLASS_CONTENT_SEC -> "USB class for content security devices"
            UsbConstants.USB_CLASS_CSCID -> "USB class for content smart card devices"
            UsbConstants.USB_CLASS_HID -> "USB class for human interface devices (for example, mice and keyboards)"
            UsbConstants.USB_CLASS_HUB -> "USB class for USB hubs"
            UsbConstants.USB_CLASS_MASS_STORAGE -> "USB class for mass storage devices"
            UsbConstants.USB_CLASS_MISC -> "USB class for wireless miscellaneous devices"
            UsbConstants.USB_CLASS_PER_INTERFACE -> "USB class indicating that the class is determined on a per-interface basis"
            UsbConstants.USB_CLASS_PHYSICA -> "USB class for physical devices"
            UsbConstants.USB_CLASS_PRINTER -> "USB class for printers"
            UsbConstants.USB_CLASS_STILL_IMAGE -> "USB class for still image devices (digital cameras)"
            UsbConstants.USB_CLASS_VENDOR_SPEC -> "Vendor specific USB class"
            UsbConstants.USB_CLASS_VIDEO -> "USB class for video devices"
            UsbConstants.USB_CLASS_WIRELESS_CONTROLLER -> "USB class for wireless controller devices"
            else -> "Unknown USB class!"
        }
    }
}





