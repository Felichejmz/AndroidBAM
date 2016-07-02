package xyz.feliche.androidbam;

/**
 * Created by feliche on 27/06/16.
 */
public class Mensajes {
    private String numero;
    private String code;

    public Mensajes(){
    }
    public Mensajes(String numero, String code){
        this.numero = numero;
        this.code = code;
    }

    public String getCode() {
        return code;
    }
    public void setCode(String code) {
        this.code = code;
    }
}
