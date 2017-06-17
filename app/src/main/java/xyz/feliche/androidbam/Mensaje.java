package xyz.feliche.androidbam;

/**
 * Created by feliche on 05/05/17.
 */

public class Mensaje {
    private String destino;
    private String contenido;
    private boolean valido, newUser;

    public Mensaje(){
        valido = false;
        destino = "";
        contenido = "";
        newUser = false;
    }

    public void set(String msg) {
        valido = false;
        destino = "";
        contenido = "";
        newUser = false;

        if(msg.isEmpty())return;

        if(msg.contains(Const.SEPARADOR_PEDIDO) == true){
            destino = msg.split(Const.SEPARADOR_PEDIDO)[0];
            contenido = msg.split(Const.SEPARADOR_PEDIDO)[1];
            if(contenido.length() > Const.MAX_LONG_TEXT) return;
            if(destino.length() != Const.SIZE_CELL_NUMBER) return;
            newUser = false;
            valido = true;
        }
        if(msg.contains(Const.SEPARADOR_NEW_USER) == true){
            destino = msg.split(Const.SEPARADOR_NEW_USER)[0];
            contenido = msg.split(Const.SEPARADOR_NEW_USER)[1];
            if(contenido.length() != Const.LONG_CODE) return;
            if(destino.length() != Const.SIZE_CELL_NUMBER) return;
            newUser = true;
            valido = true;
        }
    }

    public boolean isPedido(){
        if(newUser == true)
            return false;
        else
            return true;
    }

    public boolean isNuevoUsuario(){
        if(newUser == true)
            return true;
        else
            return false;
    }

    public boolean isValido(){
        if(valido == true)
            return true;
        else
            return false;
    }

    public String getDestino(){
        return destino;
    }

    public String getContenido(){
        return contenido;
    }
}
