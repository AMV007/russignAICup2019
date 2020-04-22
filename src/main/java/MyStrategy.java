import model.*;

import java.util.*;

public class MyStrategy {
  private static Game game;
  private static Debug debug;

  private static boolean dontShoot=false; // for debug purposes

  private static int boardWidth;
  private static int boardHeight;
  private static double moveSpeedPerTick;
  private static double jumpUpSpeedPerTick;
  private static double jumpFallSpeedPerTick;
  private static double secondsPerTick;
  private static double ticksPerSecond;
  private static int maxJumpTicks;
  private static final double increase_explosion_radius=1.1; // for some reason need to increase it, may be due to calculations TODO: investigate

  private static HistoryInfo prevMoveInfo = new HistoryInfo(); // for previous move info

  //region Mathematic helper classes and functions
  private static double distance(Vec2Double a, Vec2Double b) {
    double dx=a.getX() - b.getX();
    double dy=a.getY() - b.getY();
    return Math.sqrt((dx*dx) + (dy*dy));
  }

  private static double getAngleToTarget(Vec2Double source, Vec2Double target){
    double dx = target.getX() - source.getX();
    double dy = target.getY() - source.getY();
    return Math.atan2(dy, dx);
  }

  // line1 ps1 to pe1, line2 ps2 to pe2
  private final static Vec2DoubleInt getLinesIntersection(Vec2Double ps1, Vec2Double pe1, Vec2Double ps2, Vec2Double pe2)
  {
    // Get A,B of first line - points : ps1 to pe1
    double A1 = pe1.getY()-ps1.getY();
    double B1 = ps1.getX()-pe1.getX();
    // Get A,B of second line - points : ps2 to pe2
    double A2 = pe2.getY()-ps2.getY();
    double B2 = ps2.getX()-pe2.getX();

    // Get delta and check if the lines are parallel
    double delta = A1*B2 - A2*B1;
    if(delta == 0) return null;

    // Get C of first and second lines
    double C2 = A2*ps2.getX()+B2*ps2.getY();
    double C1 = A1*ps1.getX()+B1*ps1.getY();
    //invert delta to make division cheaper
    double invDelta = 1/delta;
    // now return the Vector2 intersection point
    return new Vec2DoubleInt( (B2*C1 - B1*C2)*invDelta, (A1*C2 - A2*C1)*invDelta );
  }

  // this class to increase usability with Vector2D, one reason - it not have clone
  private final static class Vec2DoubleInt extends Vec2Double{

    public Vec2DoubleInt(){
      this.setX(0);
      this.setY(0);
    }
    public Vec2DoubleInt(double X, double Y){
      this.setX(X);
      this.setY(Y);
    }

    public Vec2DoubleInt(Vec2Double value){
      this(value.getX(), value.getY());
    }

    public Vec2DoubleInt clone(){
      return new Vec2DoubleInt(getX(), getY());
    }

    @Override
    public boolean equals(Object object){
      boolean isEqual= false;

      if (object != null && object instanceof Vec2Double) {
        isEqual = (this.getX() == ((Vec2Double) object).getX())
            && (this.getY() == ((Vec2Double) object).getY());
      }

      return isEqual;
    }

    public void addX(double val){
      setX(getX()+val);
    }

    public void addY(double val){
      setY(getY()+val);
    }

    public void add(Vec2Double val){
      setX(getX()+val.getX());
      setY(getY()+val.getY());
    }

    public void sub(Vec2Double val){
      setX(getX()-val.getX());
      setY(getY()-val.getY());
    }

    public void multiply(double val){
      setX(getX()*val);
      setY(getY()*val);
    }

    public void invertDirection(){
      setX(-getX());
      setY(-getY());
    }

    public double getVectorLength(){
      return Math.sqrt(getX()*getX()+ getY()*getY());
    }

    public double distanceTo(Vec2Double vec){
      return distance(this, vec);
    }

    @Override
    public String toString() {
      return "X: "+String.format("%2.4f",getX())+",Y: "+String.format("%2.4f",getY());
    }
  }

  private final static class Rectangle{
    public double left, right, bottom, top;
    public double width, height;

    public Rectangle(double left, double right, double bottom, double top){
      this.left=left;
      this.right=right;
      this.top=top;
      this.bottom=bottom;
      this.width=right-left;
      this.height=top-bottom;
    }

    public Rectangle clone(){
      return new Rectangle(left,right,bottom,top);
    }

    public static Rectangle fromSomePos(Vec2Double pos, Vec2Double size){
      double left=pos.getX()-size.getX()/2;
      double right=left+size.getX();
      double bottom=pos.getY();
      double top = pos.getY()+size.getY();

      Rectangle unitRect = new Rectangle(left, right, bottom, top);
      return unitRect;
    }

    public static Rectangle fromUnit(Unit unit){
      return fromSomePos(unit.getPosition(), unit.getSize());
    }

    public static Rectangle fromMine(Mine mine){
      return fromSomePos(mine.getPosition(), mine.getSize());
    }

    public static Rectangle fromBullet(Vec2Double pos, double size){
      double left=pos.getX()-size/2;
      double right=left+size;
      double bottom=pos.getY()-size/2;
      double top = bottom+size;

      Rectangle unitRect = new Rectangle(left, right, bottom, top);
      return unitRect;
    }

    public static Rectangle fromExplosion(Vec2Double pos, double size){
      double left=pos.getX()-size;
      double right=pos.getX()+size;
      double bottom=pos.getY()-size;
      double top = pos.getY()+size;

      Rectangle unitRect = new Rectangle(left, right, bottom, top);
      return unitRect;
    }

    public static Rectangle fromMineExplosion(Mine mine){
      double checkRadius=mine.getExplosionParams().getRadius();
      Vec2DoubleInt minePos=new Vec2DoubleInt(mine.getPosition());
      minePos.addY(mine.getSize().getY()/2);
      return fromExplosion(minePos, checkRadius);
    }

    public static Rectangle fromTile(int X, int Y){
      double left=X;
      double right=X+1;
      double bottom=Y;
      double top = Y+1;

      Rectangle unitRect = new Rectangle(left, right, bottom, top);
      return unitRect;
    }

    public Vec2Double getUnitPos(){
      double X = left+width/2;
      double Y = bottom;
      return new Vec2DoubleInt(X,Y);
    }

    public Vec2DoubleInt getCenterPos(){
      double X = left+width/2;
      double Y = bottom+height/2;
      return new Vec2DoubleInt(X,Y);
    }

    // point is inside rectangle
    public boolean intersectWithPoint(double X, double Y){
      if(X>=left && X<=right && Y>=bottom && Y<=top){
        return true;
      }
      return false;
    }

    // intersects with another rectangle
    public boolean intersectWithRect(Rectangle rect){
      // If one rectangle is on left side of other
      if (left > rect.right || rect.left > right) {
        return false;
      }

      // If one rectangle is above other
      if (top < rect.bottom || rect.top < bottom) {
        return false;
      }

      return true;
    }

    // intersects with circle
    public boolean intersectWithCircle(Vec2Double center, double radius) {
      double circleDistanceX = Math.abs(center.getX() - (left + width / 2));
      double circleDistanceY = Math.abs(center.getY() - (bottom + height / 2));

      if (circleDistanceX > (width / 2 + radius)) {
        return false;
      }
      if (circleDistanceY > (height / 2 + radius)) {
        return false;
      }

      if (circleDistanceX <= (width / 2)) {
        return true;
      }
      if (circleDistanceY <= (height / 2)) {
        return true;
      }

      double cornerDistance_sq = Math.pow((circleDistanceX - width / 2), 2) +
          Math.pow((circleDistanceY - height / 2), 2);

      return (cornerDistance_sq <= (radius * radius));
    }

    private Vec2DoubleInt getSideIntersectionPoint(Vec2Double bulletPos, Vec2Double velocity, Vec2Double side1, Vec2Double side2) {
      Vec2Double nextBulletPos=new Vec2Double (bulletPos.getX()+velocity.getX(),bulletPos.getY()+velocity.getY());
      Vec2DoubleInt currInterPoint = getLinesIntersection(bulletPos, nextBulletPos, side1, side2);
      if (currInterPoint == null) return null;


      double minY = Math.min(side1.getY(), side2.getY());
      double maxY = Math.max(side1.getY(), side2.getY());
      double minX = Math.min(side1.getX(), side2.getX());
      double maxX = Math.max(side1.getX(), side2.getX());

      // check point inside rectangle side
      // because mathematicals calculations there may be small error in currInterPoint in 9 digit after zero
      // so added additional check for line same coordinates
      if(side1.getX()!= side2.getX()){
        if (currInterPoint.getX() < minX || currInterPoint.getX() > maxX) return null;
      }
      if(side1.getY()!=side2.getY()) {
        if (currInterPoint.getY() < minY || currInterPoint.getY() > maxY) return null;
      }

      // check right bullet direction, also if velocity near 1e-15 there is error in calculations beam and
      // in result false checking
      if(Math.abs(velocity.getX())>1e-10)
        if(Math.signum(currInterPoint.getX()-bulletPos.getX())!=Math.signum(velocity.getX())) return null;
      if(Math.abs(velocity.getY())>1e-10)
        if(Math.signum(currInterPoint.getY()-bulletPos.getY())!=Math.signum(velocity.getY())) return null;

      return currInterPoint;
    }

    private Vec2DoubleInt intersectionWithDotBullet(Vec2Double bulletPos, Vec2Double bulletVelocity)
    {
      double distance=-1;
      Vec2DoubleInt intersectionPoint=null;

      Vec2DoubleInt currInterPoint = getSideIntersectionPoint(bulletPos, bulletVelocity, getBottomLeft(), getBottomRight());
      if(currInterPoint!=null){
        // there is intersection
        double distanceToBullet=currInterPoint.distanceTo(bulletPos);
        if(distance==-1||distanceToBullet<distance){
          distance=distanceToBullet;
          intersectionPoint=currInterPoint;
        }
      }

      currInterPoint = getSideIntersectionPoint(bulletPos, bulletVelocity, getBottomLeft(), getTopLeft());
      if(currInterPoint!=null){
        // there is intersection
        double distanceToBullet=currInterPoint.distanceTo(bulletPos);
        if(distance==-1||distanceToBullet<distance){
          distance=distanceToBullet;
          intersectionPoint=currInterPoint;
        }
      }
      currInterPoint = getSideIntersectionPoint(bulletPos, bulletVelocity, getTopRight(), getTopLeft());
      if(currInterPoint!=null){
        // there is intersection
        double distanceToBullet=currInterPoint.distanceTo(bulletPos);
        if(distance==-1||distanceToBullet<distance){
          distance=distanceToBullet;
          intersectionPoint=currInterPoint;
        }
      }
      currInterPoint = getSideIntersectionPoint(bulletPos, bulletVelocity, getTopRight(), getBottomRight());
      if(currInterPoint!=null){
        // there is intersection
        double distanceToBullet=currInterPoint.distanceTo(bulletPos);
        if(distance==-1||distanceToBullet<distance){
          distance=distanceToBullet;
          intersectionPoint=currInterPoint;
        }
      }

      return intersectionPoint;
    }

    public static class resIntersectWithSquareBullet {
      public Vec2DoubleInt intersectPoint;
      public Vec2DoubleInt quadBulletPoint; // position of bullet corner
      public Vec2DoubleInt bulletPosAtIntersect; // position of bullet center
      public Vec2DoubleInt mineCenterPosition; // is we will hit mine
      public double distance; // distance between this 2 vectors
      public int unitIDIntersectWith; // in function intersectWithUnit - will be filled only
      public HitTarget hitTarget; // not allways right, TODO: make better logic
      public resIntersectWithSquareBullet(HitTarget hitTarget){
        this.hitTarget=hitTarget;
        unitIDIntersectWith=-1;
      }
    }

    // by conditions - bullet is square
    resIntersectWithSquareBullet intersectionWithSquareBullet(Vec2Double bulletPos, Vec2Double bulletVelocity, double bulletSize){
      resIntersectWithSquareBullet res=null;
      Vec2DoubleInt[] bulletCorners=Rectangle.fromBullet(bulletPos, bulletSize).getCorners();
      // let's check, that any vector from bullet corner will intersect unit and select with minimum intersect distance
      for (Vec2DoubleInt pos : bulletCorners){
        Vec2DoubleInt intersection=intersectionWithDotBullet(pos, bulletVelocity);
        if(intersection!=null){
          double distance = distance(pos, intersection);
          if (res==null||distance<res.distance){
            res = new resIntersectWithSquareBullet(HitTarget.targetRect);
            res.distance=distance;
            res.intersectPoint =intersection;
            res.quadBulletPoint=pos;
          }
        }
      }
      if(res!=null){
        res.bulletPosAtIntersect = new Vec2DoubleInt(bulletPos);
        res.bulletPosAtIntersect.addX(res.intersectPoint.getX()-res.quadBulletPoint.getX());
        res.bulletPosAtIntersect.addY(res.intersectPoint.getY()-res.quadBulletPoint.getY());
      }
      return res;
    }

    public void move(MoveDirection direction, double val){
      Vec2DoubleInt offset = new Vec2DoubleInt(0,0);
      switch (direction){
        case up: offset = new Vec2DoubleInt(0, val); break;
        case down: offset = new Vec2DoubleInt(0, -val); break;
        case right: offset = new Vec2DoubleInt(val, 0); break;
        case left:offset = new Vec2DoubleInt(-val, 0); break;
      }
      move(offset);
    }

    public void move(Vec2DoubleInt offset){
      top+=offset.getY();
      bottom+=offset.getY();
      left+=offset.getX();
      right+=offset.getX();
    }

    //region rectangle corners
    public Vec2DoubleInt getBottomLeft(){
      return new Vec2DoubleInt(left, bottom);
    }

    public Vec2DoubleInt getBottomRight(){
      return new Vec2DoubleInt(right, bottom);
    }

    public Vec2DoubleInt getTopLeft(){
      return new Vec2DoubleInt(left, top);
    }

    public Vec2DoubleInt getTopRight(){
      return new Vec2DoubleInt(right, top);
    }
    //endregion

    //region rectangle middle
    public Vec2Double getMiddleBottom(){
      return new Vec2Double(left+width/2, bottom);
    }

    public Vec2Double getMiddleRight(){
      return new Vec2Double(right, bottom+height/2);
    }

    public Vec2Double getMiddleTop(){
      return new Vec2Double(left+width/2, top);
    }

    public Vec2Double getMiddleLeft(){
      return new Vec2Double(left, bottom+height/2);
    }
    //endregion

    public Vec2DoubleInt[] getCorners(){
      return new Vec2DoubleInt[]{
          getBottomLeft(),
          getTopLeft(),
          getBottomRight(),
          getTopRight()
      };
    }
  }

  //Calculate bullet intercept position according current enemy movement
  Vec2DoubleInt getInterceptPointOfMovingTarget(Vec2Double targetSpeed, double bulletSpeed, Vec2Double targetPos, Vec2Double unitPos){
    return getInterceptPointOfMovingTarget(targetSpeed.getX(),targetSpeed.getY(), bulletSpeed, unitPos.getX(), unitPos.getY(),
        targetPos.getX(), targetPos.getY());
  }

  Vec2DoubleInt getInterceptPointOfMovingTarget(double Vx, double Vy, double s , double Tx, double Ty, double Ax, double Ay){
    double a=Vx*Vx +Vy*Vy - s*s;
    double b=2* (Vx*(Ax - Tx) + Vy*(Ay - Ty));
    double c=(Ay - Ty)*(Ay - Ty) + (Ax - Tx)*(Ax - Tx);
    double D = b*b - 4 * a * c;
    if(D<0){
      return null;
    }

    double t1 = (-b + Math.sqrt(D))/ 2 * a;
    double t2 = (-b - Math.sqrt(D))/ 2 * a;

    double t=Math.min(t1,t2);
    if(t<0) t=Math.max(t1,t2);
    if(t<0){
      return null;
    }

    // intercept point
    double X = t * Vx + Ax;
    double Y = t * Vy + Ay;

    return new Vec2DoubleInt(X,Y); // new possible enemy position to meet with our bullet based on calculations
  }
  //endregion Mathematic helper classes and functions

  //region Helper classes and functions
  private enum MoveReason {
    plantMine,
    avoidMine,
    notSaveLand, // this must be above all other avoidings, otherwise can move down dangerously TODO: investigate
    avoidBulletTwoWay,
    avoidBullet,
    needHeal,
    noWeapon,
    aboveJumpPad,
    cantShootTooClose,
    stop,// ---------------- all above this reason are critical
    findBetterWeapon,
    tooCloseToTarget,
    movingToNearEnemy,
    movingToEnemyAirUnk,
    tooCloseToFriend,
    none
  }

  private enum MoveDirection{
    up,
    left,
    right,
    down,
  }

  private enum HitTarget{
    unit,
    targetRect,
    wall,
    mine,
  }

  private enum DebugColors{
    red,
    green,
    blue,
    white,
    yellow,
  }


  private static String getConstStringFromBoolean(boolean val){
    if (val)
      return "1";
    return "0";
  }

  private Vec2DoubleInt getBulletVelocityPerTick(Vec2Double BulletSpeed){
    double tps=game.getProperties().getTicksPerSecond();
    return new Vec2DoubleInt(BulletSpeed.getX()/tps, BulletSpeed.getY()/tps );
  }

  private static Tile getTile(Vec2Double pos){
    return getTile(pos.getX(), pos.getY());
  }

  private static Tile getTile(double X, double Y){
    return game.getLevel().getTiles()[(int) X ][(int) Y];
  }

  Tile getNotEmptyTileUnderPoint(Vec2Double position){
    double posY=position.getY();
    // get distance to nearest non empty tile
    while(posY>=0){
      Tile currTile=getTile(position.getX(),posY);
      if ( currTile != Tile.EMPTY){
        return currTile;
      }
      posY-=0.1;
    }
    return null; // this actually must not happen
  }

  private static double getDistanceToNotEmptyTileUnderPoint(Vec2Double position){
    double posY=position.getY();
    // get distance to nearest non empty tile
    while(posY>=0){
      Tile currTile=getTile(position.getX(),posY);
      if ( currTile != Tile.EMPTY&&currTile != Tile.JUMP_PAD){
        return position.getY()-((int)posY+1);
      }
      posY-=0.1;
    }
    return 0; // this actually must not happen
  }

  private static boolean isPointInsideBoard(Vec2Double point){
    if (point.getX()<0||point.getX()>=boardWidth) return false;
    if (point.getY()<0||point.getY()>=boardHeight) return false;
    return true;
  }

  private static boolean isRectangleOutsideBoard(Rectangle rect) {
    for (Vec2DoubleInt corner : rect.getCorners()) {
      if (!isPointInsideBoard(corner)) return true;
    }
    return false;
  }

  private static boolean isRectangleAtLadder(Rectangle rect){
    for (Vec2DoubleInt corner : rect.getCorners()) {
      if (getTile(corner) == Tile.LADDER) {
        double ladderXPos = ((int) corner.getX()) + 0.5;
        if (ladderXPos >= rect.left && ladderXPos <= rect.right) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isRectangleInsideWall(Rectangle rect) {
    for (Vec2DoubleInt corner : rect.getCorners()) {
      if (getTile(corner) == Tile.WALL) {
        return true;
      }
    }
    return false;
  }

  private static boolean isRectangleInAir(Rectangle rect) {
    // because tile ladder not giving us understand, that on the ladder
    if (isRectangleAtLadder(rect)){
      return false;
    }
    for (Vec2DoubleInt corner : rect.getCorners()) {
      if (getTile(corner) == Tile.WALL) {
        return false;
      }
    }

    double offsetYLeft = getDistanceToNotEmptyTileUnderPoint(rect.getBottomLeft());
    double offsetYRight = getDistanceToNotEmptyTileUnderPoint(rect.getBottomRight());
    if (offsetYLeft>=0&&offsetYLeft<0.03 && offsetYRight>=0&&offsetYRight<0.03) // it's not only for tiles, but for
      return false;

    return true;
  }

  Rectangle.resIntersectWithSquareBullet getNearestTileWallIntersection(Bullet bullet){
    return getNearestTileWallIntersection(bullet.getPosition(), bullet.getVelocity(), bullet.getSize());
  }

  Rectangle.resIntersectWithSquareBullet getNearestTileWallIntersection(Vec2Double bulletPos, Vec2Double bulletSpeed, double bulletSize){
    Rectangle.resIntersectWithSquareBullet intersectionPoint=null;

    int startPositionX=(int)bulletPos.getX();
    int startPositionY=(int)bulletPos.getY();
    // to analyze also tiles near unit before - bazooka can hit it when shoot at close angle
    // because we are moving a bit far from angle - back tiles not analyzed
    startPositionX-=Math.signum(bulletSpeed.getX());
    startPositionY-=Math.signum(bulletSpeed.getY());

    // searching wall tile with minimum intersect distance with quad bullet
    for(int X=startPositionX;X<boardWidth&&X>=0;X+=Math.signum(bulletSpeed.getX())){
      for(int Y=startPositionY;Y<boardHeight&&Y>=0;Y+=Math.signum(bulletSpeed.getY())){
        if (getTile(X,Y) == Tile.WALL){
          Rectangle rect = Rectangle.fromTile(X,Y);
          Rectangle.resIntersectWithSquareBullet currIntersect = rect.intersectionWithSquareBullet(bulletPos, bulletSpeed, bulletSize);
          if(currIntersect!=null){
            if(intersectionPoint==null||currIntersect.distance<intersectionPoint.distance){
              intersectionPoint=currIntersect;
              intersectionPoint.hitTarget=HitTarget.wall;
            }
          }
        }
        if(bulletSpeed.getY()==0) break; // we passing all X with only current Y
      }
      if(bulletSpeed.getX()==0) break; // we passing all Y only with current X
    }
    return intersectionPoint;
  }

  public static boolean contains(final int[] array, final int v) {
    boolean result = false;
    for(int i : array){
      if(i == v){
        return true;
      }
    }
    return false;
  }

  //get nearest mine intersection, exclude source and target units, because target hit will be analyzed in another function
  Rectangle.resIntersectWithSquareBullet getNearestMineIntersection(Vec2Double bulletPos, Vec2Double bulletSpeed, double bulletSize) {
    Rectangle.resIntersectWithSquareBullet intersectionPoint = null;
    for (Mine mine : game.getMines()) {
      if (mine.getState() == MineState.PREPARING) continue;

      Rectangle mineRect = Rectangle.fromMine(mine);

      Rectangle.resIntersectWithSquareBullet currIntersect = mineRect.intersectionWithSquareBullet(bulletPos, bulletSpeed, bulletSize);
      if (currIntersect != null) {
        if (intersectionPoint == null || currIntersect.distance < intersectionPoint.distance) {
          intersectionPoint = currIntersect;
          intersectionPoint.hitTarget = HitTarget.mine;
          intersectionPoint.mineCenterPosition = new Vec2DoubleInt(mine.getPosition().getX(), mine.getPosition().getY() + mine.getSize().getY() / 2);
        }
      }
    }
    return intersectionPoint;
  }

  //get nearest unit intersection, exclude source and target units, because target hit will be analyzed in another function
  Rectangle.resIntersectWithSquareBullet getNearestUnitIntersection(Vec2Double bulletPos, Vec2Double bulletSpeed, double bulletSize,
                                                                    int[] ignoreUnitID // array of ignored IDs - susally source and target
  ){
    Rectangle.resIntersectWithSquareBullet intersectionPoint=null;
    for (Unit unit : game.getUnits()){
      if(contains(ignoreUnitID, unit.getId())) continue; // not analyzing ignoreing units

      Rectangle unitRect = Rectangle.fromUnit(unit);

      Rectangle.resIntersectWithSquareBullet currIntersect = unitRect.intersectionWithSquareBullet(bulletPos, bulletSpeed, bulletSize);
      if(currIntersect!=null){
        if(intersectionPoint==null||currIntersect.distance<intersectionPoint.distance){
          intersectionPoint=currIntersect;
          intersectionPoint.unitIDIntersectWith=unit.getPlayerId();
          intersectionPoint.hitTarget=HitTarget.unit;
        }
      }
    }
    return intersectionPoint;
  }

  private boolean isPossibleMove(Unit unit, MoveDirection direction, double distance){
    Rectangle unitRect = Rectangle.fromUnit(unit);
    int maxTicks = (int)(distance/moveSpeedPerTick);
    for (int i=1;i<=maxTicks;i++){
      if(!isPossibleMove(unitRect, unit.getId(), direction, i)){
        return false;
      }
      unitRect.move(direction, moveSpeedPerTick);
    }
    return true;
  }

  private boolean isPossibleMove(Rectangle unitRect, int unitID, MoveDirection direction, int bulletTickAdd){
    Rectangle nextRect = unitRect.clone();
    nextRect.move(direction, moveSpeedPerTick); // we can't change source rectangle

    // analyzing that unit inside board and not inside wall
    if( isRectangleOutsideBoard(nextRect)|| isRectangleInsideWall(nextRect)) return false; //isRectangleOutsideBoard must be first

    // check intersect with other players - is it possible to move or not ?
    for (Unit other : game.getUnits()) {
      if (other.getId() == unitID) continue; // not analyzing our unit here
      Rectangle otherRect = Rectangle.fromUnit(other);
      if (nextRect.intersectWithRect(otherRect)) return false;
    }

    // check intersect with bullets don't move here !!?? this is rough - TODO: improve accuracy here ?
    for (Bullet bullet : game.getBullets()) {
      if (bullet.getUnitId()!= unitID) {
        Vec2DoubleInt BulletSpeedPerTick=getBulletVelocityPerTick(bullet.getVelocity());
        BulletSpeedPerTick.multiply(bulletTickAdd);

        Vec2DoubleInt nextBulletPos = new Vec2DoubleInt(bullet.getPosition());
        nextBulletPos.add(BulletSpeedPerTick);

        Rectangle nextBulletRect = Rectangle.fromBullet(nextBulletPos, bullet.getSize());

        //check next bullet inside board - site tests fail
        if(isRectangleOutsideBoard(nextBulletRect)) continue;

        // checking, that we will not intersect with bullet
        if(nextRect.intersectWithRect(nextBulletRect)) return false;

        // checking that we are not moving to dangerous area
        if((nextRect.intersectionWithSquareBullet(nextBulletPos, bullet.getVelocity(), bullet.getSize())!=null ||
            nextRect.intersectionWithSquareBullet(bullet.getPosition(), bullet.getVelocity(), bullet.getSize())!=null)
            &&unitRect.intersectionWithSquareBullet(bullet.getPosition(), bullet.getVelocity(), bullet.getSize())==null
        ) return false;

        if(bullet.getExplosionParams()!=null){
          // actually it's very roughly, and it's must be checked at bullet hit, but may save life for unit ?
          if(getTile(nextBulletPos)==Tile.WALL){
            // bullet will explode at next tick, get it's intersection point with wall tile
            Rectangle.resIntersectWithSquareBullet intersection=getNearestTileWallIntersection(bullet);
            if(intersection!=null){
              double explRadius=bullet.getExplosionParams().getRadius()*increase_explosion_radius;
              Rectangle explosiveRect = Rectangle.fromExplosion(intersection.bulletPosAtIntersect, explRadius);
              if (nextRect.intersectWithRect(explosiveRect)){
                // explosion will hit us
                return false;
              }
            }
          }
        }
      }
    }

    // analyze mines
    for (Mine mine : game.getMines()) {
      // this all dangerous states of mines, let's check, that we will not hit mine position
      Rectangle explosionRect = Rectangle.fromMineExplosion(mine);
      if (nextRect.intersectWithRect(explosionRect)) {
        // explosion will hit us
        // but check that we are inside explosion already in this case it's possible moveout fro mexplosion range
        if(!unitRect.intersectWithRect(explosionRect)){
          return false;
        }
      }
    }
    return true;
  }

  private static class ResultHitByBullet {
    public boolean canHit;
    public boolean directHit; // hit directly our unit with intersection
    public int hitTicks;
    public Rectangle.resIntersectWithSquareBullet intersectionParam; // nearest something hit
  }

  // returning number of ticks before bullet will hit
  ResultHitByBullet analyzeBulletHitUnitRect(Vec2Double bulletPos, Vec2Double bulletSpeed, ExplosionParams explParam,
                                             double bulletSize, Rectangle unitRect, int[] ignoreUnitID){

    ResultHitByBullet res = new ResultHitByBullet(); // allways return non null
    Rectangle.resIntersectWithSquareBullet analyzeIntersect=null;
    Rectangle.resIntersectWithSquareBullet [] intersections = new Rectangle.resIntersectWithSquareBullet[]{
        unitRect.intersectionWithSquareBullet(bulletPos, bulletSpeed, bulletSize),
        getNearestTileWallIntersection(bulletPos, bulletSpeed,bulletSize),
        getNearestUnitIntersection(bulletPos, bulletSpeed,bulletSize, ignoreUnitID),
        getNearestMineIntersection(bulletPos, bulletSpeed,bulletSize),
    };

    for (Rectangle.resIntersectWithSquareBullet intersection : intersections){
      if (intersection!=null){
        debugDrawPoint(intersection.intersectPoint, DebugColors.yellow);
        if(analyzeIntersect==null || intersection.distance<analyzeIntersect.distance){
          analyzeIntersect=intersection;
        }
      }
    }

    if(analyzeIntersect==null) {
      // will not hit anything
      return res;
    }
    // we will hit something, let's keep analyze it
    res.intersectionParam=analyzeIntersect;
    debugDrawPoint(analyzeIntersect.intersectPoint, DebugColors.red);

    if(analyzeIntersect.hitTarget == HitTarget.targetRect){
      // bullet will directly hit target rect
      res.canHit =true;
      res.directHit = true;
    }else {
      if(analyzeIntersect.hitTarget == HitTarget.mine) {
        // analyze, that we will hit mine ?
        double explRadius = game.getProperties().getMineExplosionParams().getRadius();
        Rectangle explosionRect = Rectangle.fromExplosion(analyzeIntersect.mineCenterPosition, explRadius);
        // let's check that we can hit with explosion
        if (unitRect.intersectWithRect(explosionRect)) {
          res.canHit = true;
        }
      }
      if (explParam != null) {
        double explRadius = explParam.getRadius() * increase_explosion_radius;
        Rectangle explosionRect = Rectangle.fromExplosion(analyzeIntersect.bulletPosAtIntersect, explRadius);
        // let's check that we can hit with explosion
        if (unitRect.intersectWithRect(explosionRect)) {
          res.canHit = true;
        }
      }
    }

    if (res.canHit) {
      // calculate ticks before unitRect will be hit
      Vec2DoubleInt bulletVelocityPerTick = getBulletVelocityPerTick(bulletSpeed);
      res.hitTicks = (int) (analyzeIntersect.distance / bulletVelocityPerTick.getVectorLength());
    }
    return res;
  }

  final static class AvoidResult{
    public boolean canAvoid;
    public boolean notPossibleMove;
    public boolean noNeedToAvoid;
    public int ticks;
    public MoveDirection direction;

    public AvoidResult(MoveDirection direction){
      this.direction=direction;
    }
  }

  AvoidResult getMoveTicksToAvoidBullet(Unit unit, Bullet bullet, MoveDirection direction){
    AvoidResult res = new AvoidResult(direction);
    Rectangle unitRect=Rectangle.fromUnit(unit);
    Vec2DoubleInt bulletVelocityPerTick = getBulletVelocityPerTick(bullet.getVelocity());
    Vec2DoubleInt bulletPos = new Vec2DoubleInt(bullet.getPosition());

    for(int i=1;
        unitRect.left>=0&&unitRect.right<=boardWidth
            &&unitRect.bottom>=0&&unitRect.bottom<=boardHeight
            &&bulletPos.getX()>=0&&bulletPos.getX()<=boardWidth
            &&bulletPos.getY()>=0&&bulletPos.getY()<=boardHeight
        ;i++){
      // no more possible move to this direction
      if(!isPossibleMove(unitRect, unit.getId(), direction,i)){
        res.ticks =i-1;
        res.notPossibleMove=true;
        return res;
      }

      ResultHitByBullet canBeHit= analyzeBulletHitUnitRect(bulletPos, bullet.getVelocity(),bullet.getExplosionParams(),
          bullet.getSize(), unitRect,
          new int[]{unit.getId(), bullet.getUnitId()});
      if(!canBeHit.canHit){
        res.canAvoid=true;
        res.ticks =i; // +1 - there is error in hit ticks calculation, it not linear ??? TODO: check this error
        return res;
      }

      double moveSpeed=0; // usual jump state
      switch(direction){
        case up:moveSpeed=jumpUpSpeedPerTick; break;
        case down:moveSpeed=jumpFallSpeedPerTick; break;
        case left:
        case right:
          moveSpeed=moveSpeedPerTick;
          break;
      }
      if(isRectangleAtLadder(unitRect)) moveSpeed=moveSpeedPerTick; // on the ladder speed will be same as moving speed              }

      unitRect.move(direction, moveSpeed);
      bulletPos.add(bulletVelocityPerTick);
    }

    res.noNeedToAvoid=true;
    return res; // bullet will nto hit target
  }
  //endregion Helper classes and functions

  //region Pathfinder helper functions
  // for pathfinder - get unitID for target position for ignore as blocking to find path
  private int getUnitID(Vec2Double pos){
    for (Unit unit: game.getUnits()){
      if((int)unit.getPosition().getX()==(int)pos.getX()
          &&(int)unit.getPosition().getY()==(int)pos.getY()
      ){
        return unit.getId();
      }
    }
    return -1;
  }

  private static boolean isTileInAir(int X, int Y, ArrayList<Integer> ignoreBlockUnitID){
    Rectangle tileUnitRect=Rectangle.fromSomePos(new Vec2Double(X+0.5,Y), game.getProperties().getUnitSize());
    return isRectangleInAir(tileUnitRect);
  }

  private static boolean isTileJumpPad(int X, int Y, ArrayList<Integer> ignoreBlockUnitID){
    return getTile(X,Y)==Tile.JUMP_PAD;
  }

  private static boolean  isTileBlocked(int X, int Y, ArrayList<Integer> ignoreBlockUnitID){
    // check that tile is not wall
    Rectangle tileUnitRect=Rectangle.fromSomePos(new Vec2Double(X+0.5,Y), game.getProperties().getUnitSize());
    if (isRectangleOutsideBoard(tileUnitRect)||isRectangleInsideWall(tileUnitRect)) return true; // isRectangleOutsideBoard must be first !

    //check, that there are no players at tile
    for (Unit unit: game.getUnits()){
      if(ignoreBlockUnitID.contains(unit.getId())) continue; // ignore current unit and target unit for pathfinder block othwrwise it not working
      Rectangle unitRect = Rectangle.fromUnit(unit);
      if(unitRect.intersectWithRect(tileUnitRect)){
        return true;
      }
    }

    // check that there are no armed mines at tile
    // not workign with pathfinder - unit trying to jump
    for (Mine mine: game.getMines()) {
      // it's possible to jump over not triggered mines
      Rectangle tileRect = Rectangle.fromTile(X, Y);
      // this all dangerous states of mines, let's check, that we will not hit mine position
      Rectangle explosionRect = Rectangle.fromMineExplosion(mine);
      if (tileRect.intersectWithRect(explosionRect)) {
        return true;
      }
    }
    return false;
  }
  //endregion Pathfinder helper functions

  //region History helper
  private static class HistoryInfo {
    private int infoTick=-1; // -1 - to force update at 0 tick of the game
    private HashMap <Integer, PreviousMoveParam> prevMove;
    private HashMap <Integer, PreviousMoveParam> currMove;
    private HashMap <Integer, PathFinder> pathFinder;

    private static class PreviousMoveParam {
      public final Vec2DoubleInt position;
      public Vec2Double aimVector;
      public boolean readyToShoot;
      public int notMovedTicks;
      public int notShootedTicks;

      public PreviousMoveParam(Vec2Double position){
        this.position=new Vec2DoubleInt(position);
        this.aimVector = new Vec2Double(0,0); // we will change it later
      }
    }

    // time to move param from current to previous may be
    synchronized public void checkTick(Game game, int playerID){
      if (infoTick==game.getCurrentTick()) return; // all positions already updated, no need for update
      infoTick=game.getCurrentTick();

      if (prevMove==null){
        //this is first launch, fill  currMovewith current positions
        currMove = new HashMap<>();
        getCurrentUnitsPositions(game);

        pathFinder = new HashMap<>();
        for (Unit unit : game.getUnits()) {
          if (unit.getPlayerId() != playerID) continue; // we are ignoring enemy units

          PathFinder finder = new AStarPathFinder(new GameMap(), 500, false);
          pathFinder.put(unit.getId(), finder);

          PreviousMoveParam param = new PreviousMoveParam(unit.getPosition());
          currMove.put(unit.getId(), param);
        }
      }

      // checking not move and not shoot
      if (prevMove!=null) {
        for (Map.Entry<Integer, PreviousMoveParam> entry : currMove.entrySet()) {
          int uniID = entry.getKey();
          PreviousMoveParam currParam = entry.getValue();
          PreviousMoveParam prevParam = prevMove.get(uniID);
          if (prevParam.position.equals(currParam.position)) {
            currParam.notMovedTicks = prevParam.notMovedTicks+1;
          } else {
            currParam.notMovedTicks = 0;
          }

          if (!prevParam.readyToShoot && !currParam.readyToShoot) {
            currParam.notShootedTicks=prevParam.notShootedTicks+1;;
          } else {
            currParam.notShootedTicks = 0;
          }
        }
      }

      prevMove=currMove;
      currMove = new HashMap<>();
      getCurrentUnitsPositions(game);
    }

    private void getCurrentUnitsPositions(Game game){
      // updating current positions
      for (Unit unit : game.getUnits()) {
        PreviousMoveParam param = new PreviousMoveParam(unit.getPosition());
        currMove.put(unit.getId(), param);
      }
    }

    public void setNewParam(UnitInternal unit) {
      currMove.get(unit.unit.getId()).aimVector = unit.target.hitResult.aimVector;
      currMove.get(unit.unit.getId()).readyToShoot = unit.target.hitResult.readToShoot();
    }

    public Vec2DoubleInt getLastPos(Unit unit){
      return new Vec2DoubleInt(prevMove.get(unit.getId()).position);
    }

    public Vec2Double getLastAim(Unit unit){
      return prevMove.get(unit.getId()).aimVector;
    }

    public int getLastNotShootTicks(Unit unit){
      return prevMove.get(unit.getId()).notShootedTicks;
    }

    public int getLastNotMovedTicks(Unit unit){
      return prevMove.get(unit.getId()).notMovedTicks;
    }

    public Vec2DoubleInt getPos(Unit unit){
      return new Vec2DoubleInt(currMove.get(unit.getId()).position);
    }

    public Vec2DoubleInt getUnitSpeed(Unit unit){
      Vec2DoubleInt currPos=getPos(unit);
      Vec2DoubleInt lastPos=getLastPos(unit);
      return new Vec2DoubleInt(
          currPos.getX()-lastPos.getX(),
          currPos.getY()-lastPos.getY());
    }

    public Vec2DoubleInt getUnitSpeedPerSec(Unit unit){
      Vec2DoubleInt speed=getUnitSpeed(unit);
      speed.multiply(ticksPerSecond);
      return speed;
    }

    public PathFinder getPathFinder(Unit unit){
      return pathFinder.get(unit.getId());
    }
  }
  //endregion History helper

  // region Debug helper
  private static ColorFloat getDebugColor(DebugColors color){
    switch (color){
      case red: return new ColorFloat(255,0,0,200);
      case green: return new ColorFloat(0,255,0,200);
      case blue: return new ColorFloat(0,0,255,200);
      case white: return new ColorFloat(255,255,255,200);
      case yellow: return new ColorFloat(255,255,0,200);
      default: return new ColorFloat(100,100,100,200);
    }
  }

  private static Vec2Float getFrom(Vec2Double point1){
    return new Vec2Float((float)point1.getX(), (float)point1.getY());
  }

  private static void debugDrawPoint(Vec2Double point, DebugColors color){
    debugDrawSquare(point,0.1 ,color);
  }

  private static void debugDrawSquare(Vec2Double point, double size, DebugColors color){
    ColorFloat col = getDebugColor(color);
    CustomData.Rect debugRect=new CustomData.Rect(
        new Vec2Float((float)(point.getX()-size/2),(float)(point.getY()-size/2)),
        new Vec2Float((float)size,(float)size), col);
    debug.draw(debugRect);
  }

  private static void debugDrawLine(Vec2Double point1, Vec2Double point2, DebugColors color){
    ColorFloat col = getDebugColor(color);
    CustomData.Line debugLine=new CustomData.Line(getFrom(point1), getFrom(point2),
        0.05f, col);
    debug.draw(debugLine);
  }

  private static void debugDrawText(Vec2Double point, int val, DebugColors color){
    debugDrawText(point, String.format("%2d",val),color);
  }

  private static void debugDrawText(Vec2Double point, double val, DebugColors color){
    if (val<1)
      debugDrawText(point, String.format("%1.1f",val),color);
    else
      debugDrawText(point, String.format("%2.0f",val),color);
  }

  private static void debugDrawText(Vec2Double point, String text, DebugColors color){
    ColorFloat col = getDebugColor(color);
    CustomData.PlacedText dbgText = new CustomData.PlacedText(text, getFrom(point), TextAlignment.CENTER,20f, col);
    debug.draw(dbgText);
  }
  // endregion debug helper

  // this class - try to structurize action work
  final class UnitInternal {

    //region class variables
    public final Unit unit;
    public final Vec2Double  unitSize;
    final Vec2DoubleInt unitPosition;
    public int bulletHitTicks=99; // ticks before nearest bullet will hit
    final WeaponType FAVORITE_WEAPON;

    private Vec2DoubleInt targetPos; // can modify only from inside
    private boolean targetJumpUp; // can modify only inside
    private boolean targetJumpDown; // can modify only inside
    private double velocity;

    public boolean swapWeapon;

    public TargetParam target;

    public Unit nearestFriend;
    public double nearestFriendDistance=99;

    public Mine nearestMine;

    public Weapon weapon;
    public boolean isWeaponEmpty;
    public double weaponExplosionRange;
    // actually it's here, because need to display stats and I don't want to check weapon not null
    public int magazine;
    public double weaponBulletSpeed;// also here to not check for null
    public double aimSpeed;
    public double aimSpread;

    // unit analyze results
    public ArrayList<MoveDirection> canMove = new ArrayList<>();

    public boolean isNotMoved;
    public boolean isOnTheGround;
    public boolean isAboveJumpPad;
    public boolean isOnLadder;
    public boolean isSafeLand;
    public boolean canShootAtJumpDownFinish;

    MoveDirection avoidBulletDirection;

    HashMap<MoveDirection, Integer> avoidTicks = new HashMap<>(); // for debug avoid
    //endregion

    public class TargetParam{
      public Unit ourUnit;
      public Unit unit;
      public double distance =99;
      public Vec2Double interceptPoint;
      public CanHitResult hitResult;

      public TargetParam(Unit ourUnit, Unit targetUnit){
        this.ourUnit=ourUnit;
        this.unit=targetUnit;
        this.distance = distance(ourUnit.getPosition(), targetUnit.getPosition());
        this.interceptPoint = getEnemyPredictedShootPosition(targetUnit);
        this.hitResult = analyzeHitTarget(ourUnit.getPosition(), interceptPoint, targetUnit.getSize(), targetUnit);
      }

      public void tryShootMine(Mine mine){
        CanHitResult readyToShootMine= analyzeHitTarget(ourUnit.getPosition(),mine.getPosition(),
            mine.getSize(), null);
        if (readyToShootMine.readToShoot()){
          hitResult =readyToShootMine;
        }
      }
    }

    private class MoveReasonParam{
      public MoveReason reason;
      public MoveDirection dir;
      public MoveReasonParam(MoveReason reason, MoveDirection dir){
        this.reason=reason;
        this.dir=dir;
      }

      @Override
      public boolean equals(Object object)
      {
        boolean isEqual= false;

        if (object != null && object instanceof MoveReasonParam)
        {
          isEqual = (this.reason == ((MoveReasonParam) object).reason)
              && (this.dir == ((MoveReasonParam) object).dir);
        }
        return isEqual;
      }

      @Override
      public String toString() {
        return ""+reason+(dir!=null?"|"+dir:"");
      }
    }

    private ArrayList<MoveReasonParam> moveReason = new ArrayList<>();
    private ArrayList<MoveReason> targetReason = new ArrayList<>();

    public void addTarget(Vec2Double target, MoveReason reason){
      if(target!=null) {
        // applying target by enum priority - lowest ordinal - highest
        boolean highestReasonApplied = false;
        for (MoveReason temp_reason : targetReason){
          if (temp_reason.ordinal()<reason.ordinal()){
            highestReasonApplied=true;
            break;
          }
        }
        if(!highestReasonApplied) {
          targetPos = new Vec2DoubleInt(target);
        }
      }
      targetReason.add(reason);
    }

    private Comparator<MoveReasonParam> MoveReasonComparator = new Comparator<MoveReasonParam>() {
      public int compare(MoveReasonParam s1, MoveReasonParam s2) {
        return s1.reason.ordinal()-s2.reason.ordinal();
      }
    };

    // just move in direction with maximum speed
    public void addMoveReason(MoveDirection direction, MoveReason reason){
      if(direction!=null) {
        // applying target by enum priority - lowest ordinal - highest
        if (moveReason.size()==0
            ||moveReason.get(0).reason.ordinal()>=reason.ordinal()){
          switch (direction){
            case right: velocity = game.getProperties().getUnitMaxHorizontalSpeed(); break;
            case left: velocity = -game.getProperties().getUnitMaxHorizontalSpeed(); break;
            case up:  targetJumpUp=true; break;
            case down: targetJumpDown=true; break;
          }
        }
      }
      // to avoid duplicates because will use unstack
      MoveReasonParam param = new MoveReasonParam(reason, direction);
      if(!moveReason.contains(param)) {
        moveReason.add(param);
        moveReason.sort(MoveReasonComparator);
      }
    }

    private String arrayListToString(ArrayList array){
      StringBuilder res= new StringBuilder();
      for (Object reason : array){
        res.append(reason).append(",");
      }
      return res.toString();
    }

    public boolean moveReasonExist(MoveReason reason){
      MoveReasonParam param = new MoveReasonParam(reason, null);
      return  moveReason.contains(param);
    }

    public String getMoveReason(){
      return arrayListToString(moveReason);
    }

    public MoveReason getTargetReason(){
      MoveReason reason=MoveReason.none;
      for (MoveReason tmpReason:targetReason){
        if(tmpReason.ordinal()<reason.ordinal()){
          reason=tmpReason;
        }
      }
      return reason;
    }

    public String getTargetReasonStr(){
      return arrayListToString(targetReason);
    }

    // we can't stop with this reasons
    public boolean isMoveReasonCritical(){
      if (moveReason.size()>0
          &&moveReason.get(0).reason.ordinal()<MoveReason.stop.ordinal()){
        return true;
      }
      return false;
    }

    public boolean getJumpUp(){
      // don't check this, because we must try jump anyway ?
      //need to ignore jump up not possible, because will not jump over at most top position
      if (!isMoveReasonCritical()){
        // check that we can shoot
        if(moveReasonExist(MoveReason.stop)){
          return false;
        }
      }
      return targetJumpUp;
    }

    public boolean getJumpDown(){
      if(!canMove.contains(MoveDirection.down)){
        return false;
      }
      if (!isMoveReasonCritical()){
        // check that we can shoot
        if(moveReasonExist(MoveReason.stop)){
          return false;
        }
      }
      return targetJumpDown;
    }

    // velocity coefficient so unit will not stack
    public void unstackUnit(double unstackVelocity){
      velocity+=unstackVelocity;
    }

    public double getVelocity(){
      // check possible bulet hit next move
      if(     velocity>0&&!canMove.contains(MoveDirection.right)
          ||velocity<0&&!canMove.contains(MoveDirection.left)
      ){
        return 0;
      }
      if (!isMoveReasonCritical()){
        // check that we can shoot
        if(moveReasonExist(MoveReason.stop)){
          return 0;
        }
      }
      return velocity;
    }

    public Vec2DoubleInt getTargetPos(){
      if(targetPos == null) {
        return unitPosition; // stay and don't move
      }
      return targetPos;
    }

    public Vec2DoubleInt[] getNearestBulletPosAndVector(){
      Vec2DoubleInt[] res = new Vec2DoubleInt[2];
      double distance=-1;
      for(Bullet bullet: game.getBullets()){
        //if(bullet.getUnitId()!=unit.getId()){  // will analyze all bullets include friendly
        double bullet_distance=distance(bullet.getPosition(), unit.getPosition());
        if(distance==-1||bullet_distance<distance){
          distance=bullet_distance;
          res[0]= new Vec2DoubleInt(bullet.getPosition());
          res[1]= new Vec2DoubleInt(bullet.getVelocity());
        }
        //}
      }
      return res;
    }

    public Vec2DoubleInt getNearestMinePos(){
      Vec2DoubleInt res = null;
      double distance=-1;
      for(Mine mine: game.getMines()){
        Vec2DoubleInt minePos=new Vec2DoubleInt(mine.getPosition());
        minePos.addY(mine.getSize().getY()/2);
        double mine_distance=distance(minePos, unit.getPosition());
        if(distance==-1||mine_distance<distance){
          distance=mine_distance;
          res= minePos;
        }
      }
      return res;
    }

    public MoveDirection getRunAwayFromX(Vec2Double pos){
      if(unit.getPosition().getX()>pos.getX()) {
        return MoveDirection.right;
      }
      else{
        return MoveDirection.left;
      }
    }

    public MoveDirection getRunToX(Vec2Double pos){
      if(unit.getPosition().getX()<=pos.getX()) {
        return MoveDirection.right;
      }
      else{
        return MoveDirection.left;
      }
    }
    //endregion

    private void checkWeapon(){
      if(unit.getWeapon()!=null) {
        weapon = unit.getWeapon();
        aimSpeed=weapon.getParams().getAimSpeed();
        aimSpread=weapon.getSpread();
        weaponBulletSpeed=weapon.getParams().getBullet().getSpeed();
        magazine = weapon.getMagazine();

        if (weapon.getParams().getExplosion() != null) {
          // weapon with explosive detected
          weaponExplosionRange = weapon.getParams().getExplosion().getRadius();
        }

        if (magazine == 0) {
          isWeaponEmpty = true;
        }
      }
    }

    // position where to shoot, so our bullet will meet enemy according it's speed
    private Vec2DoubleInt getEnemyPredictedShootPosition(Unit enemyUnit) {
      Vec2DoubleInt nearestEnemySpeed = prevMoveInfo.getUnitSpeed(enemyUnit);
      if (nearestEnemySpeed.getX()==0&&nearestEnemySpeed.getY()==0){
        //no need for analyze, just exit
        return new Vec2DoubleInt(enemyUnit.getPosition());
      }

      double weaponBulletSpeedPerTick = weaponBulletSpeed / game.getProperties().getTicksPerSecond();
      Vec2DoubleInt interceptPoint = getInterceptPointOfMovingTarget(nearestEnemySpeed, weaponBulletSpeedPerTick,
          enemyUnit.getPosition(), unit.getPosition());
      if (interceptPoint == null) {
        // if predicted shoot not available - just suppose shoot to next enemy position
        interceptPoint = new Vec2DoubleInt(enemyUnit.getPosition());
        interceptPoint.add(nearestEnemySpeed);
      }

      // let's check, that it possible move enemy to new position
      Vec2DoubleInt startPos= new Vec2DoubleInt(enemyUnit.getPosition());
      Rectangle tempEnemyRect = Rectangle.fromSomePos(startPos, enemyUnit.getSize());
      nearestEnemySpeed.multiply(0.5); // slow down for more pecious movement

      while (!isRectangleOutsideBoard(tempEnemyRect) && !isRectangleInsideWall(tempEnemyRect)
        // also checking that enemy will land on the platform
      ) { // isRectangleOutsideBoard must be first

        if (nearestEnemySpeed.getY() < 0) { // checking that enemy will not move down the platform or jumppad after jump over
          if (getTile(tempEnemyRect.getBottomRight()) == Tile.PLATFORM || getTile(tempEnemyRect.getBottomLeft()) == Tile.PLATFORM
              || getTile(tempEnemyRect.getBottomRight()) == Tile.JUMP_PAD || getTile(tempEnemyRect.getBottomLeft()) == Tile.JUMP_PAD
          ) {
            break;
          }
        }
        // while enemy rectangle will be inside wall - move it in back direction
        tempEnemyRect.move(nearestEnemySpeed);

        Vec2DoubleInt oldPos =startPos.clone();
        startPos.add(nearestEnemySpeed);
        if (  Math.signum(interceptPoint.getX()-oldPos.getX())!=Math.signum(interceptPoint.getX()-startPos.getX())
            ||Math.signum(interceptPoint.getY()-oldPos.getY())!=Math.signum(interceptPoint.getY()-startPos.getY())
        ){
          // we reached intercept point and all ok, let's exit
          startPos=interceptPoint;
          break;
        }
      }

      return startPos;
    }

    private void checkUnits(){

      // check friendly units
      for (Unit testUnit : game.getUnits()) {
        if (testUnit.getId()==unit.getId()) continue; // not analyzing our unit here ;)
        if (testUnit.getPlayerId() != unit.getPlayerId()) continue; // friendly only

        if (nearestFriend == null || distance(unit.getPosition(), testUnit.getPosition())
            < nearestFriendDistance) {
          nearestFriend = testUnit;
          nearestFriendDistance=distance(unit.getPosition(), testUnit.getPosition());
        }
      }

      TargetParam nearestEnemy=null;
      // find enemy with predicted shot
      for (Unit testUnit : game.getUnits()) {
        if (testUnit.getId() == unit.getId()) continue; // not analyzing our unit here ;)
        if (testUnit.getPlayerId() == unit.getPlayerId()) continue; // enemy

        TargetParam testEnemy = new TargetParam(unit, testUnit);

        if (nearestEnemy==null|| testEnemy.distance<nearestEnemy.distance){
          nearestEnemy = testEnemy;
        }

        if (!testEnemy.hitResult.canShoot()) continue;

        target = testEnemy;

        if (target == null || testEnemy.distance < target.distance) {
          target=testEnemy;
        }
      }

      // we not found good enemy to shoot, in this case we will analyze nearest enemy
      if (target==null){
        target=nearestEnemy;
      }
    }

    private void checkLootboxes(){
      LootBox nearestWeaponLootBox=null;
      LootBox nearestHealthPackLootBox=null;

      for (LootBox lootBox : game.getLootBoxes()) {
        if (lootBox.getItem() instanceof Item.Weapon) {
          if (nearestWeaponLootBox == null || distance(unit.getPosition(), lootBox.getPosition())
              < distance(unit.getPosition(), nearestWeaponLootBox.getPosition())) {
            nearestWeaponLootBox = lootBox;
          }
        } else if (lootBox.getItem() instanceof Item.HealthPack) {
          if (nearestHealthPackLootBox == null || distance(unit.getPosition(), lootBox.getPosition())
              < distance(unit.getPosition(), nearestHealthPackLootBox.getPosition())) {
            nearestHealthPackLootBox = lootBox;
          }
        }
      }

      checkWeaponLootBox(nearestWeaponLootBox);
      checkHealthLootbox(nearestHealthPackLootBox);
    }

    // like sensing possible and safe move directions
    private void checkPossibleMoveDirections(){
      Rectangle unitRect=Rectangle.fromUnit(unit);
      if (isPossibleMove(unitRect, unit.getId(), MoveDirection.right,1)) canMove.add(MoveDirection.right);
      if (isPossibleMove(unitRect, unit.getId(), MoveDirection.left,1)) canMove.add(MoveDirection.left);
      if (isPossibleMove(unitRect, unit.getId(), MoveDirection.up,1))  canMove.add(MoveDirection.up);
      if (isPossibleMove(unitRect, unit.getId(), MoveDirection.down,1)) canMove.add(MoveDirection.down);
    }

    private boolean isWeaponNeeded(WeaponType type){
      if (FAVORITE_WEAPON!=null){
        return (FAVORITE_WEAPON==type&&unit.getWeapon().getTyp()!=FAVORITE_WEAPON);
      }
      else{
        return unit.getWeapon().getTyp().discriminant >type.discriminant;
      }
    }

    private void checkWeaponLootBox(LootBox nearestWeaponLootBox){
      if (nearestWeaponLootBox != null ) {
        Item.Weapon nearestWeapon = (Item.Weapon)nearestWeaponLootBox.getItem();
        // right now will think, that pistol is best, because bazooka got unpredictable behaviour
        if (unit.getWeapon() == null || isWeaponNeeded(nearestWeapon.getWeaponType())) {
          Vec2DoubleInt weaponTargetPos = new Vec2DoubleInt(nearestWeaponLootBox.getPosition());

          Rectangle lootboxRect=Rectangle.fromSomePos(nearestWeaponLootBox.getPosition(), nearestWeaponLootBox.getSize());
          Rectangle unitRect=Rectangle.fromUnit(unit);
          if(lootboxRect.intersectWithRect(unitRect)){
            swapWeapon = true;
          }

          if (unit.getWeapon() == null) {
            addTarget(weaponTargetPos, MoveReason.noWeapon);
          } else if (isWeaponNeeded(nearestWeapon.getWeaponType())) {
            addTarget(weaponTargetPos, MoveReason.findBetterWeapon);
          }
        }
      }
    }

    private void checkHealthLootbox(LootBox nearestHealthPackLootBox){
      // start find health box at health 70%
      if (nearestHealthPackLootBox!=null
          &&unit.getHealth()<=(game.getProperties().getUnitMaxHealth()*0.7)
      ){
        // move to nearest health pack - highest priority
        Vec2DoubleInt healthTargetPos = new Vec2DoubleInt(nearestHealthPackLootBox.getPosition());
        addTarget(healthTargetPos, MoveReason.needHeal);
      }
    }

    private void checkAboveJumpPad(){
      Rectangle unitRect = Rectangle.fromUnit(unit);
      if(getNotEmptyTileUnderPoint(unitRect.getBottomLeft()) == Tile.JUMP_PAD
          || getNotEmptyTileUnderPoint(unitRect.getBottomRight()) == Tile.JUMP_PAD){
        isAboveJumpPad = true;
        // move to target direction - need avoid jumpad
        addTarget(target.unit.getPosition(), MoveReason.aboveJumpPad);
        addMoveReason(getRunToX(target.unit.getPosition()), MoveReason.aboveJumpPad); //TODO: check why double reason here ?
      }
    }

    // analyze what happen between ticks
    private void checkLastUnitPos() {
      Vec2DoubleInt lastUnitPosition = prevMoveInfo.getLastPos(unit);
      if (lastUnitPosition.equals(unitPosition)) {
        isNotMoved = true;
      }

      Rectangle unitRect = Rectangle.fromUnit(unit);

      isOnLadder = isRectangleAtLadder(unitRect);

      double offsetYLeft = getDistanceToNotEmptyTileUnderPoint(unitRect.getBottomLeft());
      double offsetYRight = getDistanceToNotEmptyTileUnderPoint(unitRect.getBottomRight());
      if (unitPosition.getY() == lastUnitPosition.getY() // in case we are above some unit ?
          || (offsetYLeft>=0&&offsetYLeft<0.03 && offsetYRight>=0&&offsetYRight<0.03)
          || isOnLadder
      ) {
        isOnTheGround = true;
      } else {
        checkAboveJumpPad();
      }
    }

    void checkMines(){
      double nearestMineDist=99;
      double nearestMineHitEnemyDist=99;
      Mine nearestMineCanHitEnemy=null;
      Rectangle unitRect = Rectangle.fromUnit(unit);
      // analyze mines
      for (Mine mine : game.getMines()) {
        { // check nearest mine
          double currDistance = distance(unitPosition, mine.getPosition());
          if (currDistance<nearestMineDist){
            nearestMineDist=currDistance;
            nearestMine=mine;
          }
        }

        { // check nearestMineWillHitEnemy
          double currDistance = distance(unitPosition, mine.getPosition());
          for (Unit enemyUnit: game.getUnits()){
            if (enemyUnit.getPlayerId()!=unit.getPlayerId()){
              // this is enemy, let's check him
              double bulletSpeedPerTick=weaponBulletSpeed/ticksPerSecond;
              int numTicksToHitMine=(int)(currDistance/bulletSpeedPerTick);
              Vec2DoubleInt enemySpeed = prevMoveInfo.getUnitSpeed(enemyUnit);
              enemySpeed.multiply(numTicksToHitMine);

              Rectangle nextEnemyPos = Rectangle.fromUnit(enemyUnit);
              nextEnemyPos.move(enemySpeed);

              Rectangle mineExplosion=Rectangle.fromMineExplosion(mine);
              if (nextEnemyPos.intersectWithRect(mineExplosion)){
                if (currDistance<nearestMineHitEnemyDist){
                  nearestMineHitEnemyDist=currDistance;
                  nearestMineCanHitEnemy=mine;
                }
              }
            }
          }

          if (currDistance<nearestMineHitEnemyDist){
            nearestMineDist=currDistance;
            nearestMine=mine;
          }
        }

        // this all dangerous states of mines, let's check, that we will not hit mine position
        Rectangle explosionRect = Rectangle.fromMineExplosion(mine);

        // this all dangerous states of mines, let's check, that we will not hit mine position
        if (unitRect.intersectWithRect(explosionRect)) {
          // explosion will hit us, run away from it
          MoveDirection runAwayDirection= getRunAwayFromX(mine.getPosition());
          if (!canMove.contains(runAwayDirection)){
            if (canMove.contains(MoveDirection.down)){
              runAwayDirection=MoveDirection.down;
            }else if (canMove.contains(MoveDirection.up)){
              runAwayDirection=MoveDirection.up;
            }
          }
          addMoveReason(runAwayDirection, MoveReason.avoidMine);
        }
      }

      if (nearestMineCanHitEnemy!=null){
        target.tryShootMine(nearestMineCanHitEnemy);
      }
    }

    public UnitInternal(Unit newUnit) {
      //FAVORITE_WEAPON=WeaponType.ROCKET_LAUNCHER;
      //FAVORITE_WEAPON=WeaponType.ASSAULT_RIFLE;
      FAVORITE_WEAPON=null; // will use pistol weapon

      unit = newUnit;
      unitSize = unit.getSize();
      unitPosition = new Vec2DoubleInt(unit.getPosition());

      checkWeapon(); // this must be first, because all others will use weapon
      checkUnits(); // this must be second
      checkLastUnitPos();
      checkPossibleMoveDirections();
      checkLootboxes(); // must be after checkweapon
      checkMines(); // this must be ater checkUnits so target will be filled


      avoidBulletDirection = checkAvoidBullets(); // there also seet needtojump
      isSafeLand = isSafeLand();
      canShootAtJumpDownFinish=checkCanShootAfterJumpDownFinish();

      if (prevMoveInfo.getLastNotMovedTicks(unit)>=100&&prevMoveInfo.getLastNotShootTicks(unit)>=100){
        if (target.hitResult.canHit) {
          if (target.hitResult.waitingSpread()) {
            // waiting for spread - lets just shoot !
            target.hitResult.needWaitSpread = false;
          }
        }
        else{
          // may be mine blocking our way ?
          if (nearestMine!=null){
            target.tryShootMine(nearestMine);
          }
        }
      }
    }

    public final class CanHitResult {
      public boolean canHit;
      public boolean safeShootExplosive;
      public boolean needWaitSpread;

      public Vec2Double aimVector;

      public boolean canShoot(){    return canHit&&safeShootExplosive; }
      public boolean readToShoot(){ return canShoot() && !needWaitSpread; }
      public boolean waitingSpread(){ return canShoot() && needWaitSpread;}
      public boolean notSafeShootExplosive(){ return canHit&&!safeShootExplosive;}

      public CanHitResult(Weapon weapon, Vec2Double targetPos, Vec2Double unitPos){
        // allways must provide aiming angle, even if without weapon ?
        aimVector=new Vec2DoubleInt(targetPos.getX() - unitPos.getX(),
            targetPos.getY() - unitPos.getY());

        if(weapon==null) return;

        // for some reason allways better wait maximum aim ???
        if(distance(targetPos,unitPos)>13) { //for 1.8 height need 11 ticks to move up and bullet have speed 0.9 ->11*0.9
          // at close distance it not working
          if (weapon.getSpread() > weapon.getParams().getMinSpread()) {
            //otherwise unit will kill himself
            needWaitSpread = true;
          }
        }

        if (weapon.getParams().getExplosion()==null){
          safeShootExplosive =true;
        }
      }
    }

    // because  we will analyze unit at position afte jump down, unit rect provided externally
    CanHitResult analyzeHitTarget(Vec2Double ourUnitPos, Vec2Double targetPos, Vec2Double targetSize, Unit targetUnit){
      CanHitResult res = new CanHitResult(weapon, targetPos, ourUnitPos);
      if(weapon==null) return res; // no targets or no weapons

      int targetUnitID=-1;
      if (targetUnit!=null) targetUnitID=targetUnit.getId();

      Rectangle unitRect = Rectangle.fromSomePos(ourUnitPos, unit.getSize());
      Rectangle targetRectangle = Rectangle.fromSomePos(targetPos, targetSize);

      // by rules bullet coming from the center of unit
      final Vec2Double defBulletPos = unitRect.getCenterPos();

      // trying not change aim for better accuracy ?
      Vec2Double lastAimVector = prevMoveInfo.getLastAim(unit);
      Vec2Double posFromLastAimVector= new Vec2Double(defBulletPos.getX() + lastAimVector.getX(),
          defBulletPos.getY() + lastAimVector.getY());

      ArrayList<Vec2Double>checkPossibleShootPos = new ArrayList<>();

      { // possible aim points
        // if we are close - don't change aim vector so a bit improve accuracy
        if (distance(targetPos, ourUnitPos) < 9) {
          checkPossibleShootPos.add(posFromLastAimVector);
        }

        checkPossibleShootPos.add(targetRectangle.getCenterPos()); // default from middle to middle of target unit

        /* other angles to target rectangle if we can't hit middle, need to check them all
         we will not check corners, because it too hard to hit
         need to aim first in the head, because if aim at the bottom - more easy to avoid*/
        checkPossibleShootPos.add(targetRectangle.getMiddleTop());
        checkPossibleShootPos.add(targetRectangle.getMiddleLeft());
        checkPossibleShootPos.add(targetRectangle.getMiddleRight());
        checkPossibleShootPos.add(targetRectangle.getMiddleBottom());

        // for explosive weapon we can hit even around unit by hitting walls - check it
        if (weapon.getParams().getExplosion() != null) {
          double explRadius = weapon.getParams().getExplosion().getRadius() * 0.7; // slightly decrease it

          Rectangle explosiveTargetRectangle = targetRectangle.clone();
          explosiveTargetRectangle.left -= explRadius;
          explosiveTargetRectangle.right += explRadius;
          explosiveTargetRectangle.bottom -= explRadius;
          explosiveTargetRectangle.top += explRadius;

          checkPossibleShootPos.add(explosiveTargetRectangle.getMiddleTop());
          checkPossibleShootPos.add(explosiveTargetRectangle.getMiddleLeft());
          checkPossibleShootPos.add(explosiveTargetRectangle.getMiddleRight());
          checkPossibleShootPos.add(explosiveTargetRectangle.getMiddleBottom());
        }
      }

      for (Vec2Double shootTargetPoint : checkPossibleShootPos) {

        debugDrawPoint(shootTargetPoint, DebugColors.green); // where we will test aim

        double angleToTarget= getAngleToTarget(defBulletPos, shootTargetPoint);

        double bulletVelocity = weaponBulletSpeed;
        double bulletVelocityX = bulletVelocity * Math.cos(angleToTarget);
        double bulletVelocityY = bulletVelocity * Math.sin(angleToTarget);

        Vec2Double bulletSpeed = new Vec2Double(bulletVelocityX, bulletVelocityY);

        ResultHitByBullet enemyCanBeHit = analyzeBulletHitUnitRect(defBulletPos, bulletSpeed, weapon.getParams().getExplosion(),
            weapon.getParams().getBullet().getSize(), targetRectangle,
            new int[]{unit.getId(), targetUnitID});
        res.canHit = enemyCanBeHit.canHit;

        if(res.canHit) {
          // check that we will not hit friendly unit
          res.safeShootExplosive = true;

          for (Unit tmpUnit : game.getUnits()) {
            if (tmpUnit.getId() == unit.getId()) continue; // not analyzing our unit here
            if (tmpUnit.getPlayerId() != unit.getPlayerId()) continue; // we are analyzing only friendly units here

            // check that we will not directly hit friendly unit
            Rectangle friendRect = Rectangle.fromUnit(tmpUnit);
            ResultHitByBullet friendlyCanBeHit = analyzeBulletHitUnitRect(defBulletPos, bulletSpeed, weapon.getParams().getExplosion(),
                weapon.getParams().getBullet().getSize(), friendRect,
                new int[]{unit.getId(), targetUnitID});
            if (friendlyCanBeHit.canHit) {
              res.canHit = false; // don't shoot friendly units !
            }
          }

          // calculate that explosive weapon will not hit us
          if (weapon.getParams().getExplosion() != null){
            double explRadius=weapon.getParams().getExplosion().getRadius()*increase_explosion_radius;
            Rectangle explosiveRect = Rectangle.fromExplosion(enemyCanBeHit.intersectionParam.bulletPosAtIntersect, explRadius);
            if(unitRect.intersectWithRect(explosiveRect)){
              res.safeShootExplosive = false;
            }
          }

          // check, that we will not hurt us with mine hitted by bullet
          if (enemyCanBeHit.intersectionParam.hitTarget == HitTarget.mine) {
            double explRadius = game.getProperties().getMineExplosionParams().getRadius();
            Rectangle explosionRect = Rectangle.fromExplosion(enemyCanBeHit.intersectionParam.mineCenterPosition, explRadius);
            // let's check that we can hit with explosion
            if (unitRect.intersectWithRect(explosionRect)) {
              res.canHit = false;
            }
          }
        }

        if(res.canShoot()) {
          // we found good angle for hit something !
          debugDrawSquare(shootTargetPoint, 0.3, DebugColors.white);
          res.aimVector=new Vec2Double(shootTargetPoint.getX() - defBulletPos.getX(),
              shootTargetPoint.getY() - defBulletPos.getY());
          return res;
        }
      }

      return res;
    }

    MoveDirection checkAvoidBullets() {
      MoveDirection avoidDirection=null;
      Rectangle unitRect = Rectangle.fromUnit(unit);
      for (Bullet bullet : game.getBullets()) {

        //we will check all bullets, because friendly rockets also dangerous
        ResultHitByBullet canBeHit = analyzeBulletHitUnitRect(bullet.getPosition(), bullet.getVelocity(),
            bullet.getExplosionParams(), bullet.getSize(), unitRect,
            new int[]{unit.getId(), bullet.getUnitId()});

        if (!canBeHit.canHit) continue;// bullet not hitting us, ignore it
        if (bullet.getUnitId()==unit.getId() && canBeHit.directHit) continue; // we shooted bullet and it hitting us

        // update log for statiscits
        bulletHitTicks = Math.min(bulletHitTicks, canBeHit.hitTicks);

        AvoidResult avoid = null;
        ArrayList<AvoidResult> avoidRes = new ArrayList<>();
        // selecting avoid with minimum ticks
        for (MoveDirection direction : MoveDirection.values()) {
          AvoidResult tempAvoid = getMoveTicksToAvoidBullet(unit, bullet, direction);
          avoidTicks.put(direction, tempAvoid.ticks);
          avoidRes.add(tempAvoid);
          if(tempAvoid.canAvoid&&tempAvoid.ticks!=0&&(avoid==null||tempAvoid.ticks<=avoid.ticks)){
            avoid = tempAvoid;
          }
        }

        // analyzing - if it's time to avoid - avoiding
        if (avoid != null&&avoid.ticks>=canBeHit.hitTicks) {
          avoidDirection = avoid.direction;
          addMoveReason(avoid.direction,MoveReason.avoidBullet);
        }
        else if (avoid == null){
          // we can't avoid in one direction, let's try 2 directions
          // for calculating two way avoid
          AvoidResult avoidH = null;
          AvoidResult avoidV = null;
          for (AvoidResult tempAvoid: avoidRes){
            if (tempAvoid.ticks==0) continue;
            // don't move to bullet
            if (bullet.getVelocity().getX()>0&&tempAvoid.direction==MoveDirection.left) continue;
            if (bullet.getVelocity().getX()<0&&tempAvoid.direction==MoveDirection.right) continue;

            if ((tempAvoid.direction==MoveDirection.left||tempAvoid.direction==MoveDirection.right)
                &&(avoidH==null||tempAvoid.ticks<avoidH.ticks)){
              avoidH=tempAvoid;
            }

            if ((tempAvoid.direction==MoveDirection.up||tempAvoid.direction==MoveDirection.down)
                &&(avoidV==null||tempAvoid.ticks<avoidV.ticks)){
              avoidV=tempAvoid;
            }
          }

          if (avoidH!=null)
            addMoveReason(avoidH.direction,MoveReason.avoidBulletTwoWay);
          if (avoidV!=null)
            addMoveReason(avoidV.direction,MoveReason.avoidBulletTwoWay);
        }
      }
      return avoidDirection;
    }

    boolean isSafeLand(){
      if(isOnTheGround) return true;

      int maxTicks = (int)(boardHeight/Math.min(moveSpeedPerTick, jumpFallSpeedPerTick));
      for (Bullet bullet : game.getBullets()) {

        Rectangle unitRect=Rectangle.fromUnit(unit); // it will change inside cycle so need to recreate
        Vec2DoubleInt bulletVelocityPerTick = getBulletVelocityPerTick(bullet.getVelocity());
        Vec2DoubleInt bulletPos = new Vec2DoubleInt(bullet.getPosition());
        unitRect.move(MoveDirection.down,jumpFallSpeedPerTick); // quick fix - TODO:investigate why not working allways ?

        for (int i = 0; i < maxTicks; i++) {
          ResultHitByBullet canBeHit= analyzeBulletHitUnitRect(bulletPos, bullet.getVelocity(), bullet.getExplosionParams(),
              bullet.getSize(), unitRect,
              new int[]{unit.getId(), bullet.getUnitId()});
          if (canBeHit.canHit) {
            if (bullet.getUnitId()==unit.getId()&&canBeHit.directHit) continue; // we shooted bullet and it hitting us

            addMoveReason(MoveDirection.up, MoveReason.notSaveLand);
            // no need more for check
            return false;
          }

          { // analyze, that we reached down
            double offsetYLeft = getDistanceToNotEmptyTileUnderPoint(unitRect.getBottomLeft());
            double offsetYRight = getDistanceToNotEmptyTileUnderPoint(unitRect.getBottomRight());
            double offsetY = Math.min(offsetYLeft, offsetYRight);
            if (offsetY <= 0) {
              // we reached bottom - no more analyze for current bullet
              break;
            }
          }

          double moveSpeed=jumpFallSpeedPerTick;
          if(isRectangleAtLadder(unitRect)) moveSpeed=moveSpeedPerTick; // on the ladder speed will be same as moving speed              }
          unitRect.move(MoveDirection.down,moveSpeed);
          bulletPos.add(bulletVelocityPerTick);
        }
      }

      //check mines
      {
        Rectangle unitRect=Rectangle.fromUnit(unit);
        double offsetYLeft= getDistanceToNotEmptyTileUnderPoint(unitRect.getBottomLeft());
        double offsetYRight= getDistanceToNotEmptyTileUnderPoint(unitRect.getBottomRight());
        double offsetY=Math.min(offsetYLeft, offsetYRight);
        unitRect.bottom-=offsetY;

        for (Mine mine : game.getMines()) {
          // this all dangerous states of mines, let's check, that we will not hit mine position
          Rectangle explosionRect = Rectangle.fromMineExplosion(mine);
          if (unitRect.intersectWithRect(explosionRect)) {
            return false;
          }
        }
      }
      return true;
    }

    boolean checkCanShootAfterJumpDownFinish(){
      if(isOnTheGround) return false;

      // moving unit rectangle - as it will be down already
      Rectangle unitRect=Rectangle.fromUnit(unit);
      double offsetYLeft= getDistanceToNotEmptyTileUnderPoint(unitRect.getBottomLeft());
      double offsetYRight= getDistanceToNotEmptyTileUnderPoint(unitRect.getBottomRight());
      double offsetY=Math.min(offsetYLeft, offsetYRight);
      unitRect.move(MoveDirection.down,offsetY);

      // and anlyzing, that if unit will be down, it will be able to hit enemy
      CanHitResult res= analyzeHitTarget(unitRect.getUnitPos(), target.interceptPoint,
          target.unit.getSize(), target.unit);
      return res.canShoot();
    }
  }

  void updateStrategyParameters(Unit unit, Game game, Debug debug){
    this.game=game;
    this.debug=debug;
    this.boardWidth =game.getLevel().getTiles().length;
    this.boardHeight =game.getLevel().getTiles()[0].length;
    this.jumpUpSpeedPerTick = game.getProperties().getUnitJumpSpeed()/game.getProperties().getTicksPerSecond();
    this.moveSpeedPerTick = game.getProperties().getUnitMaxHorizontalSpeed()/game.getProperties().getTicksPerSecond();
    this.jumpFallSpeedPerTick = game.getProperties().getUnitFallSpeed()/game.getProperties().getTicksPerSecond();
    this.ticksPerSecond=game.getProperties().getTicksPerSecond();
    this.secondsPerTick =1/ticksPerSecond;
    this.maxJumpTicks=(int)(game.getProperties().getUnitJumpTime()*ticksPerSecond);

    prevMoveInfo.checkTick(game,unit.getPlayerId());
  }

  public UnitAction getAction(Unit unit, Game game, Debug debug) {
    updateStrategyParameters(unit, game, debug);
    UnitInternal unitInternal = new UnitInternal(unit);

    if (unitInternal.target.unit != null
        && unitInternal.weapon != null
        && unitInternal.avoidBulletDirection ==null
    ) {
      // target proximity if have weapon
      if ((unitInternal.target.distance > 10)
          || !unitInternal.target.hitResult.canHit
      ) {
        // shorter distance to target if too far away or missed shooting direction
        unitInternal.addTarget(unitInternal.target.unit.getPosition(), MoveReason.movingToNearEnemy);
      } else if ((unitInternal.target.distance < 9 )
          || !unitInternal.target.hitResult.safeShootExplosive
      ) {
        // need to increase distance to target - run away from it
        MoveDirection runDir=unitInternal.getRunAwayFromX(unitInternal.target.unit.getPosition());
        unitInternal.addMoveReason(runDir, MoveReason.tooCloseToTarget);
      }
    }

    if(unitInternal.target.hitResult.notSafeShootExplosive()){
      //too close to target with explosive weapon - need to increase distance
      // need to increase distance to target - run away from it
      MoveDirection runDir=unitInternal.getRunAwayFromX(unitInternal.target.unit.getPosition());
      unitInternal.addMoveReason(runDir, MoveReason.cantShootTooClose);
    }

    // check, that we are too close to nearest friend need to increase distance, because will hit friend unit
    // or next bazooka will kill us all
    if(!unitInternal.target.hitResult.readToShoot()&&unitInternal.nearestFriendDistance<5){
      MoveDirection runDir=unitInternal.getRunAwayFromX(unitInternal.nearestFriend.getPosition());
      unitInternal.addMoveReason(runDir, MoveReason.tooCloseToFriend);
    }

    // if we can shoot - just stop and start shooting if all ok
    if (!unitInternal.isMoveReasonCritical()
        && (unitInternal.isOnTheGround || (!unitInternal.isOnTheGround&&unitInternal.canShootAtJumpDownFinish))
        // here we are ready to shoot, but may be waiting for better spread
        && unitInternal.target.hitResult.canShoot()
    ) {
      unitInternal.addMoveReason(null, MoveReason.stop);
    }

    //check we are in the air with no reason, and after jump over can't shoot target, let's move to him
    if(unitInternal.getMoveReason().length()==0&&!unitInternal.isOnTheGround){
      if(!unitInternal.canShootAtJumpDownFinish) {
        unitInternal.addTarget(unitInternal.target.unit.getPosition(), MoveReason.movingToEnemyAirUnk);
      }
    }

    //region pathfinder
    int pathFinderSteps = 0;
    int pathFinderJumpComplexity = 0;
    if (!unitInternal.unitPosition.equals(unitInternal.getTargetPos())) {
      ArrayList<Integer> ignoreBlockUnitID = new ArrayList<>(
          Arrays.asList(unit.getId(), getUnitID(unitInternal.getTargetPos()))
      );

      PathFinder finder = prevMoveInfo.getPathFinder(unit);
      ArrayList<AStarPathFinder.Node> path = finder.findPath(new UnitMover(ignoreBlockUnitID),
          (int) unitInternal.unitPosition.getX(), (int) unitInternal.unitPosition.getY(), // unit position
          (int) unitInternal.getTargetPos().getX(), (int) unitInternal.getTargetPos().getY()); // target position

      if (path != null && path.size() > 0) {
        pathFinderSteps = path.size();
        AStarPathFinder.Node nextPoint = path.get(1);
        pathFinderJumpComplexity = nextPoint.jumpCost;

        Vec2DoubleInt nextPointVec = new Vec2DoubleInt(nextPoint.x + 0.5, nextPoint.y);

        // so our unit will be in center
        if ((int) nextPointVec.getX() > (int) unitInternal.unitPosition.getX()) {
          unitInternal.addMoveReason(MoveDirection.right, unitInternal.getTargetReason());
        } else if ((int) nextPointVec.getX() < (int) unitInternal.unitPosition.getX()) {
          unitInternal.addMoveReason(MoveDirection.left, unitInternal.getTargetReason());
        } else { // X same - need to detect that we must unstack unit
          //don't use error here, it's not working
          // possible can stuck, so center by tile for now
          double unstackVelocity = (nextPointVec.getX() - unit.getPosition().getX());
          double minVelocity = 1;
          unitInternal.unstackUnit(minVelocity * Math.signum(unstackVelocity));
        }

        if ((int) nextPointVec.getY() > (int) unitInternal.unitPosition.getY()) {
          unitInternal.addMoveReason(MoveDirection.up, unitInternal.getTargetReason());
        } else if ((int) nextPointVec.getY() < (int) unitInternal.unitPosition.getY()) {
          unitInternal.addMoveReason(MoveDirection.down, unitInternal.getTargetReason());
        } else { // Y same - need to detect, that we must move in air, let's support unit ?
          Rectangle nextUnitRect = Rectangle.fromUnit(unit);
          nextUnitRect.move(MoveDirection.right, unitInternal.getVelocity() / ticksPerSecond);
          boolean isNextUnitRectInAir = !isRectangleOutsideBoard(nextUnitRect) && isRectangleInAir(nextUnitRect);
          if ((!unitInternal.isOnTheGround || isNextUnitRectInAir) && unitInternal.canMove.contains(MoveDirection.down)) {
            unitInternal.addMoveReason(MoveDirection.up, unitInternal.getTargetReason());
          }
        }
      }
    }
    //endregion

    // check plant mine
    boolean plantMine = false;
    if (!dontShoot) {
      if (unit.getMines() != 0 && unitInternal.weapon != null && unitInternal.isOnTheGround) {

        //check kamikadze mode - how much enemies we can hit if will place mine here
        int numEnemiesCanHitByMine = 0;
        Rectangle mineExplRect = Rectangle.fromExplosion(unit.getPosition(), game.getProperties().getMineExplosionParams().getRadius());
        for (Unit enemyUnit : game.getUnits()) {
          if (enemyUnit.getPlayerId() != unit.getPlayerId()) {
            // this is enemy
            Rectangle enemyRect = Rectangle.fromUnit(enemyUnit);
            if (enemyRect.intersectWithRect(mineExplRect)) {
              ++numEnemiesCanHitByMine;
            }
          }
        }

        if ((unitInternal.target.distance > 11 && unitInternal.target.distance < 20)
            || (unitInternal.target.distance < 13 && !unitInternal.target.hitResult.canHit) // if we are close, but enemy can't hit us
            || numEnemiesCanHitByMine > 1 // kamikaze mode !
        ) {
          // if too close - enemy will hit and explode mine
          // so mine wil lbe between enemy and unit - run away and try push enemy at mine
          // also deploy mine if enemy can't hit you in this moment
          double explRadius = game.getProperties().getMineExplosionParams().getRadius();
          explRadius += unit.getSize().getY() / 2 + 0.1; // because must avoid mine complitely
          MoveDirection direction = unitInternal.getRunAwayFromX(unitInternal.target.unit.getPosition());
          if (isPossibleMove(unit, direction, explRadius)) {
            unitInternal.addMoveReason(direction, MoveReason.plantMine);
            plantMine = true;
          }
        }
      }
    }

    // stop calculating target position ----------------------------------------------------
    //region apply unit action
    UnitAction currentAction = new UnitAction();
    currentAction.setVelocity(unitInternal.getVelocity());
    currentAction.setJump(unitInternal.getJumpUp());
    currentAction.setJumpDown(unitInternal.getJumpDown());
    currentAction.setAim(unitInternal.target.hitResult.aimVector);
    if (!dontShoot) currentAction.setShoot(unitInternal.target.hitResult.readToShoot()); //debug - can commit this line
    //currentAction.setReload(unitInternal.isWeaponEmpty); // better use native reload - will be faster ???
    currentAction.setSwapWeapon(unitInternal.swapWeapon);
    currentAction.setPlantMine(plantMine);
    //endregion

    //------------------------------------------------------------------------------------------------
    // region draw debug data
    Vec2DoubleInt unitSpeed=prevMoveInfo.getUnitSpeedPerSec(unit);
    Vec2DoubleInt[] nearestBulletParam=unitInternal.getNearestBulletPosAndVector();
    double bulletDst=-1;
    if (nearestBulletParam[0]!=null)bulletDst=distance(unit.getPosition(),nearestBulletParam[0]);
    double mineDst=-1;
    if (unitInternal.getNearestMinePos()!=null)mineDst=distance(unit.getPosition(),unitInternal.getNearestMinePos());
    debug.draw(new CustomData.Log(""
        + "Tgt: " + unitInternal.getTargetPos()
        + " | Unit: " + new Vec2DoubleInt(unit.getPosition())
        + " | BPos: " + nearestBulletParam[0]
        + " | BSpeed: " + nearestBulletParam[1]
        + " | BDst: " + String.format("%4.2f", bulletDst)
        + " | TgtDst: " + String.format("%4.2f", unitInternal.target.distance)
    ));
    debug.draw(new CustomData.Log(""
        + "canMoveRight=" + getConstStringFromBoolean(unitInternal.canMove.contains(MoveDirection.right))
        + ", canMoveLeft=" + getConstStringFromBoolean(unitInternal.canMove.contains(MoveDirection.left))
        + ", canMoveTop=" + getConstStringFromBoolean(unitInternal.canMove.contains(MoveDirection.up))
        + ", canMoveDown=" + getConstStringFromBoolean(unitInternal.canMove.contains(MoveDirection.down))
        + ", isOnTheGround=" + getConstStringFromBoolean(unitInternal.isOnTheGround)
        + ", Health=" + String.format("%2d", unitInternal.unit.getHealth())
        + ", plantMine=" + getConstStringFromBoolean(plantMine)
        + ", mineDst=" + String.format("%4.2f", mineDst)

    ));
    debug.draw(new CustomData.Log(""
        + "jUp=" + getConstStringFromBoolean(unitInternal.getJumpUp())
        + ", jDown=" + getConstStringFromBoolean(unitInternal.getJumpDown())
        + ", canHit=" + getConstStringFromBoolean(unitInternal.target.hitResult.canHit)
        + ", safeSht=" + getConstStringFromBoolean(unitInternal.target.hitResult.safeShootExplosive)
        + ", waitSpr=" + getConstStringFromBoolean(unitInternal.target.hitResult.needWaitSpread)
        + ", rdySht=" + getConstStringFromBoolean(unitInternal.target.hitResult.readToShoot())
        + ", reload=" + getConstStringFromBoolean(unitInternal.isWeaponEmpty)
        + "             "
        + ", aim=" + String.format("%4.2f", unitInternal.aimSpeed)
        + "             "
        + ", spread=" + String.format("%4.2f", unitInternal.aimSpread)
        + ", magazine=" + String.format("%2d", unitInternal.magazine)
        + ", HitTick=" + String.format("%2d", unitInternal.bulletHitTicks)
        + ", avoidDir=" + String.format("%0$-5s", unitInternal.avoidBulletDirection)
    ));
    debug.draw(new CustomData.Log(""
        + "isSafeLand=" + getConstStringFromBoolean(unitInternal.isSafeLand)
        + ", usePF: " + String.format("%2d", pathFinderSteps)
        + ", PFjump: " + String.format("%2d", pathFinderJumpComplexity)
        + ", veloc=" + String.format("%4.2f", unitInternal.getVelocity())
        + ", rSpdX=" + String.format("%4.2f", unitSpeed.getX())
        + "                "
        + ", rSpdY=" + String.format("%4.2f", unitSpeed.getY())
        + "             "
        + ", rSpdABS=" + String.format("%4.2f", unitSpeed.getVectorLength())
        + ", AVL=" + String.format("%2d", unitInternal.avoidTicks.get(MoveDirection.left))
        + ", AVR=" + String.format("%2d", unitInternal.avoidTicks.get(MoveDirection.right))
        + ", AVU=" + String.format("%2d", unitInternal.avoidTicks.get(MoveDirection.up))
        + ", AVD=" + String.format("%2d", unitInternal.avoidTicks.get(MoveDirection.down))
    ));

    debug.draw(new CustomData.Log("MoveReason: " + String.format("%0$-80s", unitInternal.getMoveReason())
        + ",\t TargetReason: " + String.format("%0$-40s", unitInternal.getTargetReasonStr())

    ));
    //endregion

    //------------------------------------------------------------------------------------------------
    // all calculations over lets update last unit positions
    prevMoveInfo.setNewParam(unitInternal);

    return currentAction;
  }

  //region Pathfinder my code
  public class UnitMover implements Mover {
    private ArrayList<Integer> ignoreBlockUnitID;

    public UnitMover(ArrayList<Integer> ignoreBlockUnitID) {
      this.ignoreBlockUnitID = ignoreBlockUnitID;
    }

    public ArrayList<Integer> getIgnoreBlockUnitID() {
      return ignoreBlockUnitID;
    }
  }

  public static class GameMap implements TileBasedMap {
    /** The map width in tiles */
    public final int WIDTH=boardWidth;
    /** The map height in tiles */
    public final int HEIGHT=boardHeight;

    /** Indicator if a given tile has been visited during the search */
    private boolean[][] visited;

    /**
     * Create a new test map with some default configuration
     */
    public GameMap() {
      visited = new boolean[WIDTH][HEIGHT];
    }

    /**
     * Clear the array marking which tiles have been visted by the path
     * finder.
     */
    public void clearVisited() {
      for (int x=0;x<getWidthInTiles();x++) {
        for (int y=0;y<getHeightInTiles();y++) {
          visited[x][y] = false;
        }
      }
    }

    /**
     * @see TileBasedMap
     * visited(int, int)
     */
    public boolean visited(int x, int y) {
      return visited[x][y];
    }

    /**
     * @see TileBasedMap#isBlocked(Mover, int, int)
     */
    public boolean isBlocked(Mover mover, int x, int y) {
      return isTileBlocked(x,y, ((UnitMover)mover).getIgnoreBlockUnitID());
    }

    public boolean isJumpPad(Mover mover, int x, int y) {
      return isTileJumpPad(x,y, ((UnitMover)mover).getIgnoreBlockUnitID());
    }

    /**
     * @see TileBasedMap#isBlocked(Mover, int, int)
     */
    public boolean isInAir(Mover mover, int x, int y) {
      return isTileInAir(x,y, ((UnitMover)mover).getIgnoreBlockUnitID());
    }

    /**
     * @see TileBasedMap#getCost(Mover, int, int, int, int)
     */
    public float getCost(Mover mover, int sx, int sy, int tx, int ty) {
      return 1;
    }

    /**
     * @see TileBasedMap#getHeightInTiles()
     */
    public int getHeightInTiles() {
      return HEIGHT;
    }

    /**
     * @see TileBasedMap#getWidthInTiles()
     */
    public int getWidthInTiles() {
      return WIDTH;
    }

    /**
     * @see TileBasedMap#pathFinderVisited(int, int)
     */
    public void pathFinderVisited(int x, int y) {
      visited[x][y] = true;
    }


  }
  //endregion

  //region Pathfinder
  public static class AStarPathFinder implements PathFinder {
    /** The set of nodes that have been searched through */
    private ArrayList closed = new ArrayList();
    /** The set of nodes that we do not yet consider fully searched */
    private SortedList open = new SortedList();

    /** The map being searched */
    private TileBasedMap map;
    /** The maximum depth of search we're willing to accept before giving up */
    private int maxSearchDistance;

    /** The complete set of nodes across the map */
    private Node[][] nodes;
    /** True if we allow diaganol movement */
    private boolean allowDiagMovement;
    /** The heuristic we're applying to determine which nodes to search first */
    private AStarHeuristic heuristic;

    /**
     * Create a path finder with the default heuristic - closest to target.
     *
     * @param map The map to be searched
     * @param maxSearchDistance The maximum depth we'll search before giving up
     * @param allowDiagMovement True if the search should try diaganol movement
     */
    public AStarPathFinder(TileBasedMap map, int maxSearchDistance, boolean allowDiagMovement) {
      this(map, maxSearchDistance, allowDiagMovement, new ClosestHeuristic());
    }

    /**
     * Create a path finder
     *
     * @param heuristic The heuristic used to determine the search order of the map
     * @param map The map to be searched
     * @param maxSearchDistance The maximum depth we'll search before giving up
     * @param allowDiagMovement True if the search should try diaganol movement
     */
    public AStarPathFinder(TileBasedMap map, int maxSearchDistance,
                           boolean allowDiagMovement, AStarHeuristic heuristic) {
      this.heuristic = heuristic;
      this.map = map;
      this.maxSearchDistance = maxSearchDistance;
      this.allowDiagMovement = allowDiagMovement;

      nodes = new Node[map.getWidthInTiles()][map.getHeightInTiles()];
      for (int x=0;x<map.getWidthInTiles();x++) {
        for (int y=0;y<map.getHeightInTiles();y++) {
          nodes[x][y] = new Node(x,y);
        }
      }
    }

    /**
     * @see PathFinder#findPath(Mover, int, int, int, int)
     * @return
     */
    public ArrayList<Node> findPath(Mover mover, int sx, int sy, int tx, int ty) {

      // easy first check, if the destination is blocked, we can't get there
      if (map.isBlocked(mover, tx, ty)) {
        return null;
      }

      // initial state for A*. The closed group is empty. Only the starting
      // tile is in the open list and it'e're already there
      nodes[sx][sy].cost = 0;
      nodes[sx][sy].depth = 0;
      closed.clear();
      open.clear();
      open.add(nodes[sx][sy]);

      nodes[tx][ty].parent = null;

      // while we haven'n't exceeded our max search depth
      int maxDepth = 0;
      while ((maxDepth < maxSearchDistance) && (open.size() != 0)) {
        // pull out the first node in our open list, this is determined to
        // be the most likely to be the next step based on our heuristic
        Node current = getFirstInOpen();
        if (current == nodes[tx][ty]) {
          break;
        }

        removeFromOpen(current);
        addToClosed(current);

        // search through all the neighbours of the current node evaluating
        // them as next steps
        for (int x=-1;x<2;x++) {
          for (int y=-1;y<2;y++) {
            // not a neighbour, its the current tile
            if ((x == 0) && (y == 0)) {
              continue;
            }

            // if we're not allowing diaganol movement then only
            // one of x or y can be set
            if (!allowDiagMovement) {
              if ((x != 0) && (y != 0)) {
                continue;
              }
            }

            // determine the location of the neighbour and evaluate it
            int xp = x + current.x;
            int yp = y + current.y;

            if (!isValidLocation(mover, sx, sy, xp, yp)) {
              continue;
            }

            Node neighbour = nodes[xp][yp];

            int nextJumpCost=current.jumpCost;
            int nextJumpPadEffect=current.jumpPadEffect;
            { // analyze jump possibility
              neighbour.inAir=map.isInAir(mover, xp, yp);
              if (neighbour.inAir) {
                if (nextJumpPadEffect>0){
                  //if (y<0) continue; // not possible move down with jump pad effect -
                  //unit will not find route if give it not possible move above jumppad
                  if (y!=0) nextJumpPadEffect-=2; // decrease it only if Y moving, otherwize move at X will kill effect
                }
                if (current.movedDownInAir && y > 0)
                  continue; // not possible to jump up after jump down or not jump up before

                if (y != 0&&nextJumpPadEffect==0) { // only if jumppad not working - calculate it
                  nextJumpCost += 2;
                  nextJumpCost=(nextJumpCost/2)*2; // must be divideable by 2, after move over Y - allways by 2
                } else if (x != 0) {
                  nextJumpCost += 1;
                  if (nextJumpCost % 2 == 0) {
                    continue; // double move in horisontal not possible
                  }
                }

                // checking jump height
                if (y > 0 && nextJumpCost > 10) { // 9 instead 8 - because can move at X TODO : not jumping over wall with maximum height
                  //not possible for jump higher
                  continue;
                }

                { // checking jump length
                  int MaxJumpLen = 21; // 21 instead 20 - just to increase jump length with 1 X
                  // in case when destination is lower, than our start point Y
                  if (yp < current.startJumpY) {
                    MaxJumpLen += (current.startJumpY - yp) * 2;
                  }

                  if (nextJumpCost > MaxJumpLen) {
                    //not possible for jump longer
                    continue;
                  }
                }

                // because moving over jumpppad twice faster - it redusing moving over terrain from 1 to 0.5 ;)
                if (nextJumpPadEffect>0){
                  nextJumpCost-=0.5;
                }
              }
              else{
                if (y>0&&nextJumpCost>=10) continue; // we reached maximum height, it's not possible jump here
                nextJumpCost=0; //hot jumping - no jump penalty here anymore
              }

              if (map.isJumpPad(mover, xp, yp)){
                nextJumpPadEffect=14; // at jumppad penalty will be less for jumping
                nextJumpCost=0;
              }
            }

            // the cost to get to this node is cost the current plus the movement
            // cost to reach this node. Note that the heursitic value is only used
            // in the sorted open list
            double nextStepCost = nextJumpCost+current.cost + getMovementCost(mover, current.x, current.y, xp, yp);
            map.pathFinderVisited(xp, yp);

            // if the new cost we've determined for this node is lower than
            // it has been previously makes sure the node hasn'e've
            // determined that there might have been a better path to get to
            // this node so it needs to be re-evaluated
            if (nextStepCost < neighbour.cost) {
              if (inOpenList(neighbour)) {
                removeFromOpen(neighbour);
              }
              if (inClosedList(neighbour)) {
                removeFromClosed(neighbour);
              }
            }

            // if the node hasn't already been processed and discarded then
            // reset it's cost to our current cost and add it as a next possible
            // step (i.e. to the open list)
            if (!inOpenList(neighbour) && !(inClosedList(neighbour))) {
              neighbour.cost = nextStepCost;
              neighbour.heuristicCost = getHeuristicCost(mover, xp, yp, tx, ty);
              neighbour.jumpCost =nextJumpCost;
              neighbour.jumpPadEffect=nextJumpPadEffect;
              if (neighbour.inAir){
                neighbour.movedDownInAir=current.movedDownInAir||y<0;
                if (!current.inAir)
                  neighbour.startJumpY=current.y;
                else
                  neighbour.startJumpY=current.startJumpY;
              }
              else{
                neighbour.startJumpY=-1;
                neighbour.movedDownInAir=false;
              }

              {//debug
                //debugDrawText(new Vec2Double(xp + 0.5, yp), nextJumpCost, DebugColors.white);
              }
              maxDepth = Math.max(maxDepth, neighbour.setParent(current));
              addToOpen(neighbour);
            }
          }
        }
      }

      // since we'e've run out of search
      // there was no path. Just return null
      if (nodes[tx][ty].parent == null) {
        { // draw debug possible path lines
          ArrayList<Node> passedNodes = new ArrayList<>();
          ArrayList<Node> needToPass = new ArrayList<>();
          final double unitHeight=game.getProperties().getUnitSize().getY()/2;
          needToPass.add(nodes[sx][sy]);
          while (needToPass.size()>0){
            Node currNode=needToPass.get(0);
            passedNodes.add(currNode);
            needToPass.remove(currNode);
            for (int x=-1;x<=1;++x) {
              for (int y = -1; y <=1; ++y) {
                if ((x == 0) && (y == 0))  continue; // it's the current tile
                int nextX=currNode.x+x;
                int nextY=currNode.y+y;
                if (nextX<0||nextY<0||nextX>=map.getWidthInTiles()||nextY>=map.getHeightInTiles())
                  continue; // we are outside of the board
                Node neighbour=nodes[nextX][nextY];
                if (neighbour.parent == currNode&&!passedNodes.contains(neighbour)){
                  needToPass.add(neighbour);
                  debugDrawLine(new Vec2Double(currNode.x+0.5, currNode.y+unitHeight),
                      new Vec2Double(neighbour.x+0.5, neighbour.y+unitHeight),
                      DebugColors.blue);
                  debugDrawSquare(new Vec2Double(neighbour.x+0.5, neighbour.y+unitHeight), 0.1, DebugColors.blue);
                }
              }
            }
          }
        }
        return null;
      }

      // At this point we've definitely found a path so we can uses the parent
      // references of the nodes to find out way from the target location back
      // to the start recording the nodes on the way.
      ArrayList<Node> res = new ArrayList<>();
      Node target = nodes[tx][ty];
      while (target != nodes[sx][sy]) {
        res.add(0, target);
        target = target.parent;
      }
      res.add(0, nodes[sx][sy]); // starting node

      { // draw debug path line
        AStarPathFinder.Node oldPoint=null;
        final double unitHeight=game.getProperties().getUnitSize().getY()/2;
        for (Node step : res) {
          if (oldPoint==null)oldPoint=step;
          debugDrawLine(new Vec2Double(oldPoint.x+0.5, oldPoint.y+unitHeight),
              new Vec2Double(step.x+0.5, step.y+unitHeight),
              DebugColors.blue);
          debugDrawSquare(new Vec2Double(step.x+0.5, step.y+unitHeight), 0.1, DebugColors.blue);
          oldPoint=step;
        }
      }

      // thats it, we have our path

      return res;
    }

    /**
     * Get the first element from the open list. This is the next
     * one to be searched.
     *
     * @return The first element in the open list
     */
    protected Node getFirstInOpen() {
      return (Node) open.first();
    }

    /**
     * Add a node to the open list
     *
     * @param node The node to be added to the open list
     */
    protected void addToOpen(Node node) {
      open.add(node);
    }

    /**
     * Check if a node is in the open list
     *
     * @param node The node to check for
     * @return True if the node given is in the open list
     */
    protected boolean inOpenList(Node node) {
      return open.contains(node);
    }

    /**
     * Remove a node from the open list
     *
     * @param node The node to remove from the open list
     */
    protected void removeFromOpen(Node node) {
      open.remove(node);
    }

    /**
     * Add a node to the closed list
     *
     * @param node The node to add to the closed list
     */
    protected void addToClosed(Node node) {
      closed.add(node);
    }

    /**
     * Check if the node supplied is in the closed list
     *
     * @param node The node to search for
     * @return True if the node specified is in the closed list
     */
    protected boolean inClosedList(Node node) {
      return closed.contains(node);
    }

    /**
     * Remove a node from the closed list
     *
     * @param node The node to remove from the closed list
     */
    protected void removeFromClosed(Node node) {
      closed.remove(node);
    }

    /**
     * Check if a given location is valid for the supplied mover
     *
     * @param mover The mover that would hold a given location
     * @param sx The starting x coordinate
     * @param sy The starting y coordinate
     * @param x The x coordinate of the location to check
     * @param y The y coordinate of the location to check
     * @return True if the location is valid for the given mover
     */
    protected boolean isValidLocation(Mover mover, int sx, int sy, int x, int y) {
      boolean invalid = (x < 0) || (y < 0) || (x >= map.getWidthInTiles()) || (y >= map.getHeightInTiles());

      if ((!invalid) && ((sx != x) || (sy != y))) {
        invalid = map.isBlocked(mover, x, y);
      }

      return !invalid;
    }

    /**
     * Get the cost to move through a given location
     *
     * @param mover The entity that is being moved
     * @param sx The x coordinate of the tile whose cost is being determined
     * @param sy The y coordiante of the tile whose cost is being determined
     * @param tx The x coordinate of the target location
     * @param ty The y coordinate of the target location
     * @return The cost of movement through the given tile
     */
    public float getMovementCost(Mover mover, int sx, int sy, int tx, int ty) {
      return map.getCost(mover, sx, sy, tx, ty);
    }

    /**
     * Get the heuristic cost for the given location. This determines in which
     * order the locations are processed.
     *
     * @param mover The entity that is being moved
     * @param x The x coordinate of the tile whose cost is being determined
     * @param y The y coordiante of the tile whose cost is being determined
     * @param tx The x coordinate of the target location
     * @param ty The y coordinate of the target location
     * @return The heuristic cost assigned to the tile
     */
    public float getHeuristicCost(Mover mover, int x, int y, int tx, int ty) {
      return heuristic.getCost(map, mover, x, y, tx, ty);
    }

    /**
     * A simple sorted list
     *
     * @author kevin
     */
    private class SortedList {
      /** The list of elements */
      private ArrayList list = new ArrayList();
      /**
       * Retrieve the first element from the list
       *
       * @return The first element from the list
       */
      public Object first() {
        return list.get(0);
      }

      /**
       * Empty the list
       */
      public void clear() {
        list.clear();
      }

      /**
       * Add an element to the list - causes sorting
       *
       * @param o The element to add
       */
      public void add(Object o) {
        list.add(o);
        Collections.sort(list);
      }

      /**
       * Remove an element from the list
       *
       * @param o The element to remove
       */
      public void remove(Object o) {
        list.remove(o);
      }

      /**
       * Get the number of elements in the list
       *
       * @return The number of element in the list
       */
      public int size() {
        return list.size();
      }

      /**
       * Check if an element is in the list
       *
       * @param o The element to search for
       * @return True if the element is in the list
       */
      public boolean contains(Object o) {
        return list.contains(o);
      }
    }

    /**
     * A single node in the search graph
     */
    private class Node implements Comparable {
      /** The x coordinate of the node */
      private int x;
      /** The y coordinate of the node */
      private int y;
      /** The path cost for this node */
      private double cost;
      /** The parent of this node, how we reached it in the search */
      private Node parent;
      /** The heuristic cost of this node */
      private double heuristicCost;
      /** The search depth of this node */
      private int depth;

      public int jumpCost;  //cost for jumping
      public boolean inAir; //inAir propery
      public boolean movedDownInAir;
      public int startJumpY;
      public int jumpPadEffect;

      /**
       * Create a new node
       *
       * @param x The x coordinate of the node
       * @param y The y coordinate of the node
       */
      public Node(int x, int y) {
        this.x = x;
        this.y = y;
        this.startJumpY=y;
      }

      /**
       * Set the parent of this node
       *
       * @param parent The parent node which lead us to this node
       * @return The depth we have no reached in searching
       */
      public int setParent(Node parent) {
        depth = parent.depth + 1;
        this.parent = parent;

        return depth;
      }

      private double getCostTotal(){
        return heuristicCost + cost+jumpCost;
      }

      /**
       * @see Comparable#compareTo(Object)
       */
      public int compareTo(Object other) {
        Node o = (Node) other;

        double f = getCostTotal();
        double of = o.getCostTotal();

        if (f < of) {
          return -1;
        } else if (f > of) {
          return 1;
        } else {
          return 0;
        }
      }
    }
  }

  public static class ClosestHeuristic implements AStarHeuristic {
    /**
     * @see AStarHeuristic#getCost(TileBasedMap, Mover, int, int, int, int)
     */
    public float getCost(TileBasedMap map, Mover mover, int x, int y, int tx, int ty) {
      float dx = tx - x;
      float dy = ty - y;

      float result = (float) (Math.sqrt((dx*dx)+(dy*dy)));

      return result;
    }

  }
  // interfaces
  public interface TileBasedMap {
    /**
     * Get the width of the tile map. The slightly odd name is used
     * to distiguish this method from commonly used names in game maps.
     *
     * @return The number of tiles across the map
     */
    public int getWidthInTiles();

    /**
     * Get the height of the tile map. The slightly odd name is used
     * to distiguish this method from commonly used names in game maps.
     *
     * @return The number of tiles down the map
     */
    public int getHeightInTiles();

    /**
     * Notification that the path finder visited a given tile. This is
     * used for debugging new heuristics.
     *
     * @param x The x coordinate of the tile that was visited
     * @param y The y coordinate of the tile that was visited
     */
    public void pathFinderVisited(int x, int y);

    /**
     * Check if the given location is blocked, i.e. blocks movement of
     * the supplied mover.
     *
     * @param mover The mover that is potentially moving through the specified
     * tile.
     * @param x The x coordinate of the tile to check
     * @param y The y coordinate of the tile to check
     * @return True if the location is blocked
     */
    public boolean isBlocked(Mover mover, int x, int y);

    public boolean isJumpPad(Mover mover, int x, int y);

    public boolean isInAir(Mover mover, int x, int y);

    /**
     * Get the cost of moving through the given tile. This can be used to
     * make certain areas more desirable. A simple and valid implementation
     * of this method would be to return 1 in all cases.
     *
     * @param mover The mover that is trying to move across the tile
     * @param sx The x coordinate of the tile we're moving from
     * @param sy The y coordinate of the tile we're moving from
     * @param tx The x coordinate of the tile we're moving to
     * @param ty The y coordinate of the tile we're moving to
     * @return The relative cost of moving across the given tile
     */
    public float getCost(Mover mover, int sx, int sy, int tx, int ty);
  }

  public interface PathFinder {

    /**
     * Find a path from the starting location provided (sx,sy) to the target
     * location (tx,ty) avoiding blockages and attempting to honour costs
     * provided by the tile map.
     *
     * @param mover The entity that will be moving along the path. This provides
     * a place to pass context information about the game entity doing the moving, e.g.
     * can it fly? can it swim etc.
     *
     * @param sx The x coordinate of the start location
     * @param sy The y coordinate of the start location
     * @param tx The x coordinate of the target location
     * @param ty Teh y coordinate of the target location
     * @return The path found from start to end, or null if no path can be found.
     */
    public ArrayList<AStarPathFinder.Node> findPath(Mover mover, int sx, int sy, int tx, int ty);
  }

  public interface Mover {

  }

  public interface AStarHeuristic {

    /**
     * Get the additional heuristic cost of the given tile. This controls the
     * order in which tiles are searched while attempting to find a path to the
     * target location. The lower the cost the more likely the tile will
     * be searched.
     *
     * @param map The map on which the path is being found
     * @param mover The entity that is moving along the path
     * @param x The x coordinate of the tile being evaluated
     * @param y The y coordinate of the tile being evaluated
     * @param tx The x coordinate of the target location
     * @param ty Teh y coordinate of the target location
     * @return The cost associated with the given tile
     */

    public float getCost(TileBasedMap map, Mover mover, int x, int y, int tx, int ty);
  }
  //endregion
}
