import java.util.*;

abstract class ASTInst {
  abstract boolean toCode(int pr, int prf, String proxI);
  abstract tripleta tam( tripleta tr);
}

abstract class ASTInstAsig extends ASTInst {
  abstract boolean checkAsig( ASTTipo t);
  abstract void asigna(ASTTipo t);
  abstract void lva(info f);
}

class ASTInstImprime extends ASTInst {
  ASTExpr expr;

  //@invariant expr != null;

  //@requires e != null;
  ASTInstImprime(ASTExpr e){
    expr = e;
  }

  // Tratando de arreglar el indice de los arreglos, pero sin éxito.
  //@ requires pr % Registros.maxT > 0; 
  //@ requires prf % Registros.maxF > 0; 
  //@ requires Global.out != null; 
  boolean toCode(int pr, int prf, String proxI){
    String reg = Registros.T[pr % Registros.maxT];
    String regF = Registros.F[prf % Registros.maxF];
    ASTTipo tipo = expr.getTip();

    expr.toCode(pr,prf,proxI);

    //Recibe lo que este en result, y lo imprime de acuerdo al tipo
    if (tipo != null)
      if (tipo.isEntero()){
        Global.out.println("li $v0, 1");
        Global.out.println("move $a0, " + reg);
        Global.out.println("syscall");
      } else if  (tipo.isFloat()){
        Global.out.println("li $v0, 2");
        Global.out.println("mov.s $f12, " + regF);
        Global.out.println("syscall");
      } else if  (tipo.isBool()){
      } else if  (tipo.isChar()){
      } else if  (tipo.isString()){
        Global.out.println("li $v0, 4");
        Global.out.println("li $a0, ");
      }

    return false;
  }

  tripleta tam( tripleta tr){
    return tr;
  }
}

class ASTInstAsigExp extends ASTInstAsig {
  ASTExprLValue lvalue;
  ASTExpr exp;

  //@ invariant lvalue!=null;
  //@ invariant exp!=null;

  //@ requires l!=null && e!=null;
  ASTInstAsigExp(ASTExprLValue l, ASTExpr e){
    this.lvalue = l;
    this.exp = e;
  }

  //@ requires t!=null && exp.getTip() != null;
  public void asigna(ASTTipo t){
    // El unico casteo implicito que hay es de Entero a Flotante 
    if (t.isFloat() && exp.getTip().isEntero())
      this.exp = new ASTExprCast(exp, new ASTTipoFloat());
  }

  //@ non_null
  public String toString(){
    return " =\n"+lvalue+" "+exp;
  }
  //@ requires d!=null && exp.getTip() !=null;
  boolean checkAsig(ASTTipo d){
    return exp.getTip().equals(d) || exp.getTip().isCompatible(d);
  }

  boolean toCode(int pr, int prf, String proxI){
    String reg = Registros.T[pr % Registros.maxT];
    String reg2 = Registros.T[(pr + 1) % Registros.maxT];

    //Depende de si es tipo básico, o tipo compuesto.
    if (lvalue.getTip().isArray() || lvalue.getTip().isStruct()){
      // Si es un literal de arreglo
      if (exp instanceof ASTExprArrayCtte) {
        //Voy asignando los elementos uno a uno
        for(int i = 0; ((ASTTipoArray) lvalue.getTip()).getTam() > i; i++){
          ASTExprArrayElem elem = new ASTExprArrayElem(lvalue, ((ASTTipoArray)lvalue.getTip()).subclass, new ASTExprAritCtteInt(i));
          elem.cargaDireccion(pr,prf, proxI);

          Registros.salvar(pr + 1);
          Registros.salvarF(prf + 1);
          ((ASTExprArrayCtte) exp).toCodeI(pr + 1, prf+1, proxI, i);
          elem.modifica(pr, prf);
          Registros.restaurar(pr + 1);
          Registros.restaurarF(prf + 1);
        } 
      } else {
        //Calcula el tamaño de la estructura y le asigna byte a byte la otra estructura
        String reg3 = Registros.T[(pr + 2) % Registros.maxT];
        int tamano = lvalue.getTip().tam;
        lvalue.cargaDireccion(pr, prf, proxI);
        ((ASTExprLValue) exp).cargaDireccion(pr + 1, prf +1, proxI);
        
        Global.out.println("lw " + reg3 + ", (" + reg2 + ")");
        Global.out.println("sw " + reg3 + ", (" + reg + ")");
        for (int cont = 4; cont < tamano; cont += 4){
          Global.out.println("add " + reg + ", "+ reg +", " + 4);
          Global.out.println("add " + reg2 + ", "+ reg2 +", " + 4);
          Global.out.println("lw " + reg3 + ", (" + reg2 + ")");
          Global.out.println("sw " + reg3 + ", (" + reg + ")");
        }
      }
      //Si cambio el discriminante debo colocar activos los atributos a los que pertenece
      //if (lvalue.getTip().isStruct() || inf.obj.equals((ASTTipoStruct) lvalue.getTip()).union.tipo) {
        //Cargo la direccion de donde comienza el union
        //Calculo el tamaño del union.
        //Le asigno 0 a todo
        //Limpio todo el tamaño del union 
      //}
    } else {
          lvalue.cargaDireccion(pr,prf, proxI);
          Registros.salvar(pr + 1);
          Registros.salvarF(prf + 1);
          exp.toCode(pr + 1, prf+1, proxI);
          lvalue.modifica(pr, prf);
          Registros.restaurar(pr + 1);
          Registros.restaurarF(prf + 1);
        
    }

    return false;
  }

  void lva(info f){
    ((ASTExprId) lvalue).setInfo(f);
  }

  tripleta tam( tripleta tr){
    return tr;
  }
}

class ASTInstBloque extends ASTInst{
  public ASTInstBloque padre;
  LinkedList inst;
  SymTable t;

  //@ invariant padre!=null;
  //@ invariant inst!=null;

  //@ requires p!=null;
  ASTInstBloque(ASTInstBloque p){
    this.padre= p;
    inst = new LinkedList();
    t = null;
  }

  void addTable(SymTable t){
    this.t = t;
  }


  //@ requires e!=null;
  void add(ASTInst e){
    inst.add(e);
  }

  //@ requires l!=null;  
  void addL(LinkedList l) {
    for (int i = 0; i < l.size(); i++){
      inst.add(l.get(i));
    }
  }

  //@ non_null
  public String toString(){
    String ret = "";
    if (t != null)
      ret = t.toString();
    else
      ret = "La tabla de simbolos es null";
    int i;
    for ( i = 0; i < inst.size(); i++){
      if (inst.get(i) instanceof ASTInst){
        ret +="\n"+((ASTInst) inst.get(i));
      }
    }
    return ret;
  }

  public tripleta tam(tripleta tr){
    tripleta actual = new tripleta();
    if (t != null)
      actual = t.tam(tr);
    tripleta max = actual;
    tripleta swap = new tripleta();
    for (int i = 0; i < inst.size(); i++){
      ASTInst a =  ((ASTInst) inst.get(i));
      if (a != null)
        swap = a.tam(actual);
      if (max != null)
        max.actualiza(swap);
    }
    return max;
  }

  //@ requires t!=null;
  void printTabla(){
    System.out.println(t.toString());
  }

  //@ requires Global.out!=null;
  boolean toCode(int pr, int prf, String proxI){
    boolean us = false;
    String proxI2;
    int y = inst.size();
    for (int  i = 0; i < y; i++){
      if (i + 1 == y)
        proxI2 = proxI;
      else
        proxI2 = Global.nuevaEtiqueta();
      if (((ASTInst) inst.get(i)) instanceof ASTInstBreak){
        us = true;
        ASTInstBreak br = (ASTInstBreak) inst.get(i);
        if (br != null) 
          br.toCode(pr, prf,proxI2);
      }
      ASTInst ins = (ASTInst) inst.get(i);
      if (ins!= null && ins.toCode(pr,prf, proxI2) && (i + 1 !=y))
        Global.out.println(proxI2 +":");
    }
    return us;
  }
}

class ASTInstIf extends ASTInst{
  ASTExpr guarda;
  ASTInstBloque cuerpo;
  LinkedList elseif;
  ASTInstElse els;

  //@ invariant guarda!=null;
  //@ invariant cuerpo!=null;

  //@ requires c!=null && g!=null && l!=null;
  ASTInstIf(ASTInstBloque c, ASTExpr g, LinkedList l){
    this.cuerpo = c;
    this.guarda = g;
    if ( l != null)
      for (int i = 0; i < l.size() ; i++){
        if (l.get(i) instanceof ASTInstElses){
          if (((ASTInstElses) l.get(i)).isElse()){
            if (l.get(i) instanceof ASTInstElse){
              els =((ASTInstElse) l.get(i));
              l.remove(i);
              break;
            }
          }
        }
      }
    elseif = l;  
  }

  public  /* non_null */String toString(){
    String ret = "if"+guarda+" "+cuerpo;
    if (elseif !=null)
      for (int i = 0; i < elseif.size() ; i++ ) {
        if (elseif.get(i) instanceof ASTInst){
          ret +="\n"+((ASTInst) elseif.get(i));
        }
      }
    if (els != null)
      ret +=" "+els;
    return ret;
  }

  //@ requires Global.out != null;
  boolean toCode(int pr, int prf, String proxI){
    String y = Registros.T[pr % Registros.maxT];
    String NE = Global.nuevaEtiqueta();
    guarda.toCode(pr,prf,NE);
    Global.out.println("beqz "+y+" , "+NE);
    cuerpo.toCode(pr,prf, proxI);
    Global.out.println(" j " + proxI);
    Global.out.println(NE+":");
    if ( elseif !=null){
      int y1 = elseif.size();
      for (int i = 0; i < y1; i++){
        ASTInstElseIf eif = (ASTInstElseIf) elseif.get(i);
        if (eif != null)
          if (i + 1 == y1)
            if (els == null)
              eif.toCode(pr, prf, proxI,true,false);
            else
              eif.toCode(pr, prf, proxI,true,true);
          else
            eif.toCode(pr, prf, proxI,false,false);
      }
    }
    if(els != null)
      els.toCode(pr,prf, proxI);
    return true;
  }

  tripleta tam(tripleta tr){
    tripleta actual = cuerpo.tam(tr);
    tripleta max = cuerpo.tam(tr);
    tripleta swap = new tripleta();
    if (els != null)
      swap = els.tam(actual);
    max.actualiza(swap);
    if (elseif != null){
      for (int i = 0; i < elseif.size() ; i++){
        if (elseif.get(i) instanceof ASTInst){
          swap = ((ASTInst) elseif.get(i)).tam(actual);
          max.actualiza(swap);
        }
      }
    }
    return max;
  }
}


abstract class ASTInstElses extends ASTInst {
  abstract boolean isElse();
}

class ASTInstElseIf extends ASTInstElses{
  ASTExpr guarda;
  ASTInstBloque bloque;

  //@ invariant guarda!=null;
  //@ invariant bloque!=null;

  //@ requires g!=null && b!=null;
  ASTInstElseIf(ASTExpr g, ASTInstBloque b){
    this.guarda = g;
    this.bloque = b;
  }

  boolean isElse(){
    return false;
  }

  //@ non_null
  public String toString(){
    return " elseif "+guarda+"\n"+bloque;
  }

  boolean toCode(int pr, int prf, String proxI){
    return false;
  }

  boolean toCode(int pr, int prf, String proxI, boolean ultimo, boolean els){
    String NE = null;
    String y = Registros.T[pr % Registros.maxT];
    if (ultimo && !els)
      NE = proxI;
    else
      NE = Global.nuevaEtiqueta();
    guarda.toCode(pr,prf,NE);
    Global.out.println("beqz "+y+" , "+ NE);
    bloque.toCode(pr, prf, proxI);
    if (!ultimo || els)
      Global.out.println("j "+proxI);
    if (!ultimo || (ultimo && els))
      Global.out.println(NE+":");
    return true;
  }

  tripleta tam(tripleta tr){
    return bloque.tam(tr);
  }
}


class ASTInstElse extends ASTInstElses{
  ASTInstBloque bloque;

  //@ invariant bloque!=null;

  //@ requires b!=null;
  ASTInstElse(ASTInstBloque b){
    bloque = b;
  }

  boolean isElse(){
    return true;
  }

  //@ non_null
  public String toString(){
    return  "\n else \n"+bloque;
  }

  boolean toCode(int pr, int prf, String proxI){
    return bloque.toCode(pr, prf, proxI);
    //Sera necesario saltar a proxima instrucción?
  }

  tripleta tam(tripleta tr){
    return bloque.tam(tr);
  }
}

class ASTInstWhile extends ASTInst{
  ASTExpr guarda;
  ASTInstBloque bloque;
  //@ invariant guarda !=null;
  //@ invariant bloque !=null;

  //@ requires g!=null && b!=null;
  ASTInstWhile(ASTExpr g, ASTInstBloque b){
    this.guarda = g;
    this.bloque = b;
  }

  //@ non_null
  public String toString(){
    return "while "+guarda+" "+bloque;
  }

  boolean toCode(int pr, int prf, String proxI){
    String NE= Global.nuevaEtiqueta();
    String NE2= Global.nuevaEtiqueta();
    String y = Registros.T[pr % Registros.maxT];
    boolean us = false;
    Global.out.println("j "+NE);
    Global.out.println(NE2+":");
    if (bloque.toCode(pr,prf, proxI))
      us = true;
    Global.out.println(NE+":");
    if (guarda.toCode(pr,prf,NE2))
      us = true; 
    Global.out.println("bnez "+y+" , "+NE2);
    return us;
  }
  tripleta tam(tripleta tr){
    return bloque.tam(tr);
  }
}

class ASTInstFor extends ASTInst{
  ASTInstAsig inicial;
  ASTExpr bool;
  ASTInstAsig aumento;
  ASTInstBloque bloque;
  //@ invariant inicial!=null;
  //@ invariant bool!=null;
  //@ invariant aumento!=null;
  //@ invariant bloque!=null;

  //@ requires i!=null&& b!=null && a!=null && bl!=null;
  ASTInstFor(  ASTInstAsig i,ASTExpr b,  ASTInstAsig a,  ASTInstBloque bl){
    this.inicial = i;
    this.bool = b;
    this.aumento = a;
    this.bloque = bl;
  }

  //@ non_null
  public String toString(){
    String ret = "for ";
    if (inicial != null)
      ret +=inicial+" ";
    ret += bool+" ";
    if (aumento != null)
      ret +=aumento+" ";
    ret +=bloque;
    return ret;
  }

  boolean toCode(int pr, int prf, String proxI){
    String NE = Global.nuevaEtiqueta();
    String NE2 = Global.nuevaEtiqueta();
    String y = Registros.T[pr % Registros.maxT];
    boolean us = false;
    if ((inicial!= null) && inicial.toCode(pr, prf, NE))
      us = true;
    Global.out.println("j "+NE);
    Global.out.println(NE2+":");
    if (bloque.toCode(pr, prf, NE))
      us = true;
    if ((aumento!= null) && aumento.toCode(pr, prf,NE))
      us = true;
    Global.out.println(NE+":");
    if (bool.toCode(pr,prf,proxI))
      us = true; 
    Global.out.println("bnez "+y+" , "+NE2);
    return us;
  }

  tripleta tam(tripleta tr){
    return bloque.tam(tr);
  }
}

class ASTInstForeach extends ASTInst{
  info temp;
  info arreglo;
  ASTInstBloque bloque;

  //@ invariant guarda !=null;
  //@ invariant bloque !=null;

  //@ requires g!=null && b!=null;
  ASTInstForeach(info t, info a, ASTInstBloque b){
    this.temp = t;
    this.arreglo = a;
    this.bloque = b;
  }

  //@ non_null
  public String toString(){
    return "foreach ("+temp+" in  "+ arreglo +") "+bloque;
  }

  boolean toCode(int pr, int prf, String proxI){
    String b = Global.nuevaEtiqueta();
    //Calculo el tamaño de los elementos
    int tamano = ((ASTTipoArray) arreglo.obj).subclass.tam; 

    //Calculo el tamaño del arreglo.
    int size = ((ASTTipoArray)arreglo.obj).tam;
    String reg = Registros.T[pr % Registros.maxT];
    String reg2 =  Registros.T[(pr+1) % Registros.maxT];
    String reg3 =  Registros.T[(pr+2) % Registros.maxT];

    Global.out.println("li " +reg2 + ", 0");
    Global.out.println(b + ": ");

    arreglo.cargaDireccion(pr, prf);
    Global.out.println("add " + reg + ","+ reg2 + ","+ reg +"");

    //Ejecuto el bloque de codigo 
    bloque.toCode( pr + 3, prf, proxI);

    //Calculo de i.
    Global.out.println("add " +reg2 + ", " + reg2 + ","+tamano);
    //Salto mientras no complete los elementos de los arreglos. 
    Global.out.println("bne " + reg2 + ", "+ size + "," + b);

    return true;
  }

  tripleta tam(tripleta tr){
    return bloque.tam(tr);
  }
}

class ASTInstSwitch extends ASTInst{
  ASTExpr cas;
  LinkedList expr;
  LinkedList bloque;
  //@ invariant cas!=null && expr!=null && bloque!=null;

  //@requires c!=null && a!=null && b!=null;
  ASTInstSwitch(ASTExpr c,LinkedList a, LinkedList b){
    this.cas = c;
    expr = a;
    bloque = b;
  }


  //@ non_null
  public String toString(){
    String ret = "switch "+cas+"\n";
    for (int i = 0;  i < expr.size(); i++){
      if ((expr.get(i) instanceof ASTExpr) && (bloque.get(i) instanceof ASTInstBloque)){
        ret += "case "+((ASTExpr) expr.get(i))+" "+((ASTInstBloque) bloque.get(i))+"\n";
      }
    }
    return ret;
  }

  boolean toCode(int pr, int prf, String proxI){
    String actual = Registros.T[pr % Registros.maxT];
    String sig = Registros.T[(pr + 1) % Registros.maxT];
    String NE = Global.nuevaEtiqueta();
    
    //Buscando el valor de la expresion
    cas.toCode(pr,prf,proxI);
    
    int y = expr.size();
    for (int i = 0; i < y ;i++){
      if ( i + 1 == y){
        ASTExpr e1 = (ASTExpr) expr.get(i);
        e1.toCode(pr+1,prf + 1,proxI);
        Global.out.println("bne "+actual+","+sig+" ,"+proxI);
        ((ASTInstBloque) bloque.get(i)).toCode(pr+1,prf,proxI);
      }else{
        ASTExpr e1 = (ASTExpr) expr.get(i);
        e1.toCode(pr+1,prf,proxI);
        Global.out.println("bne "+actual+","+sig+" ,"+NE);
        ((ASTInstBloque) bloque.get(i)).toCode(pr+1,prf,proxI);
        Global.out.println("j "+proxI);
        Global.out.println(NE+":");
        NE = Global.nuevaEtiqueta();
      }
    }

    return true;
  }

  tripleta tam(tripleta tr){
    tripleta max = new tripleta();
    tripleta swap = new tripleta();
    if ( bloque != null)
      for (int i = 0; i < bloque.size();i++){
        swap = ((ASTInst) bloque.get(i)).tam(tr);
        max.actualiza(swap);
      }
    return max;
  }
}

class ASTInstFuncion extends ASTInst {
  LinkedList param;
  Proc procedimiento;

  //@ invariant param!=null;

  //@requires p!=null;
  ASTInstFuncion (LinkedList p, Proc pro){
    this.param = p;
    this.procedimiento = pro;
  }

  //@ non_null
  public String toString(){
    String ret = procedimiento.nombre+" (";
    if (param != null)
      for (int i = 0; i < param.size();i++){
        if (param.get(i) instanceof ASTExpr){
          ret +=((ASTExpr) param.get(i))+",";
        }
      }
    ret +=")";
    return ret;
  }

  boolean toCode(int pr, int prf, String proxI){
    if (procedimiento.retType!=null)
      Global.out.println("add $sp, $sp, -"  + procedimiento.retType.tam);
    
    //Salvo Registros
    Global.out.println(Registros.salvarRegistrosLlamador(pr));
    
    //Empilo parametros
    if (param != null)
      Registros.empilarParametros(param, procedimiento);

    //llamo Al otro procedimiento Guardo la dirección de retorno
    Global.out.println("sw $ra, ($sp)");
    Global.out.println("add $sp, $sp, -4");
    Global.out.println("jal fun" + procedimiento.nombre);
    Global.out.println("add $sp, $sp, 4");
    Global.out.println("lw $ra, ($sp)");
    
    //Desempilo Parametros
    if (param != null)
      Registros.desempilarParametros(procedimiento);
    
    //Restauro Registros
    Global.out.println(Registros.restaurarRegistrosLlamador(pr));

    return true;
  }

  tripleta tam(tripleta tr){
    //System.out.println("Verificar la funcion tam de ASTInstFuncion");
    return tr;
  }
}

class ASTInstBreak extends ASTInst{
  boolean toCode(int pr, int prf, String p){
    Global.out.println("j "+p);
    return true;
  }
  tripleta tam(tripleta tr){
    return tr;
  }
}

class ASTInstReturn extends ASTInst{
  ASTExpr exp;
  int retParam;
  
  ASTInstReturn(ASTExpr e){
    this.exp = e;
  }

  boolean toCode(int pr, int prf, String p){
    String reg = Registros.T[pr % Registros.maxT];
    exp.toCode(pr, prf, p);
    System.out.println(retParam);
    Global.out.println("sw "+reg+", " + retParam + "($fp)");
    Global.out.println("j " + p);
    return true;
  }

  tripleta tam(tripleta tr){
    return tr;
  }
}